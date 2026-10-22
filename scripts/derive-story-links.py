#!/usr/bin/env python3
"""
Proposes, and with --apply writes, each checklist item's `unlockedBy`: the step of the game's
Main Quest (`<game>/main_quest.json`) that has to be ticked before the item can be had. See
docs/checklist-trackers.md, *Story gating*, for what a link means and how to choose one.

    python3 -I scripts/derive-story-links.py ss              # print the review table (TSV)
    python3 -I scripts/derive-story-links.py ss --apply      # write the links into the JSON files

A proposal comes from, in order: an override for that one item, the item's `after` text, its
`area`, then its `region`, each looked up in the game's table below. Nothing matched means
"available from the start" and the item gets no link. Every row of the table is meant to be
reviewed against the game's sources before applying; the basis column says where each came from.

--apply edits the files as text, putting `"unlockedBy": [...]` after the item's `"name"`, because
most data files are laid out by hand and wouldn't survive being re-serialised. Items that
already have `unlockedBy` are left alone, so it's safe to run twice.

The convention (the same for every game):
- Inside a dungeon or area a step opens: the step that opens it, usually the one before.
- Needs a step's reward, or opened by finishing it: that step.
- Needs an item: the step that gives the item.
- Opened by optional content (a side quest, a shop): the step that opens that content.
- Tiered items: whatever the first tier needs.
- When in doubt, gate earlier: hiding something already available is the mistake to avoid.
"""
import json
import re
import sys
from pathlib import Path

FILES = Path(__file__).resolve().parent.parent / "shared/src/commonMain/composeResources/files"

# Each game: `after`, `area` and `region` texts to a step id (None: available from the start),
# and `items`, overrides by "<file stem>/<item id>".
GAMES = {
    "ss": {
        "after": {
            "Goddess Sword (when you can first fly around the Sky)": "statue_of_the_goddess",
            "After the Goddess Sword": "statue_of_the_goddess",
            "First visit to Faron Woods": "statue_of_the_goddess",
            "Deep Woods": "faron_woods",
            # The Beetle and the boss are inside the temple, which finding the Kikwis opens.
            "Skyview Temple (Beetle)": "faron_woods",
            "Skyview Temple": "faron_woods",
            "Placing the Ruby Tablet in the Statue of the Goddess": "skyview_temple",
            "Finding Kukiel": "skyview_temple",
            "Finding Kukiel (after placing the Ruby Tablet)": "skyview_temple",
            "Meeting Batreaux (Finding Kukiel)": "skyview_temple",
            "After meeting Batreaux": "skyview_temple",
            "First visit to Eldin Volcano": "skyview_temple",
            "First visit to Eldin Volcano (Digging Mitts)": "skyview_temple",
            "Earth Temple entrance": "skyview_temple",
            "Earth Temple": "key_pieces",
            "Earth Temple (Bomb Bag)": "key_pieces",
            "First visit to Lanayru": "earth_temple",
            "First visit to Lanayru Desert": "earth_temple",
            # Outside the facility, "after the LMF" is the step itself; inside it, the generator.
            "Lanayru Mining Facility": "lanayru_mining_facility",
            "Lanayru Mining Facility (Gust Bellows)": "power_generator",
            "Lanayru Mining Facility (first potion); finished after the Sandship": "lanayru_mining_facility",
            "Temple of Time": "lanayru_mining_facility",
            "Goddess's Harp": "temple_of_time",
            "Goddess's Harp (for the last job)": "statue_of_the_goddess",
            "Goddess's Harp (after the first fight with The Imprisoned)": "imprisoned_first",
            "The Imprisoned (first fight), with the Goddess's Harp": "imprisoned_first",
            "Scrapper repaired and the Isle of Songs reached": "farores_courage",
            "Dodoh's Party Wheel (Gratitude Crystal side quest)": "farores_courage",
            "Water Dragon's Scale": "trial_farore",
            "Water Dragon's Scale (top of the Great Tree)": "trial_farore",
            "Lake Floria": "trial_farore",
            "Ancient Cistern": "lake_floria",
            "Clawshots": "trial_nayru",
            "Lanayru Sand Sea": "trial_nayru",
            "Skipper's Retreat": "trial_nayru",
            "Pirate Stronghold": "trial_nayru",
            "Pirate Stronghold (Lanayru Sand Sea)": "trial_nayru",
            "Shipyard (Moldarach)": "trial_nayru",
            "Sandship": "lanayru_sand_sea",
            "Sandship (Bow) and Fledge's training": "lanayru_sand_sea",
            "Fledge's confidence (Gratitude Crystal side quest) and the Bow": "lanayru_sand_sea",
            "Volcano Summit": "trial_din",
            "Fire Sanctuary": "volcano_summit",
            "Fire Sanctuary (Mogma Mitts)": "volcano_summit",
            "Fire Sanctuary, and Pumm's chores begun": "fire_sanctuary",
            "Freeing Levias (Bilocyte)": "levias",
            "Freeing Levias in the Thunderhead": "levias",
            "Freeing Levias (Bug Heaven open)": "levias",
            "Freeing Levias; finished after Faron's part of the Song of the Hero": "levias",
            "During the Song of the Hero": "levias",
            "Eldin's part of the Song of the Hero": "levias",
            "Lanayru's part of the Song of the Hero": "levias",
            "Giving the Thunder Dragon the Life Tree Fruit": "hero_song_lanayru",
            # Beedle's Airshop is reached with the Sailcloth.
            "Life Medal bought from Beedle": "wing_ceremony",
        },
        "area": {
            "Beedle's Airshop": "wing_ceremony",
            "Batreaux's House": "skyview_temple",
            "Faron Woods": "statue_of_the_goddess",
            "Sealed Temple": "statue_of_the_goddess",
            "Skyview Temple": "faron_woods",
            "Eldin Volcano": "skyview_temple",
            "Earth Temple": "key_pieces",
            "Lanayru Mining Facility": "power_generator",
            "Ancient Cistern": "lake_floria",
            "Lanayru Desert": "earth_temple",
            "Sandship": "lanayru_sand_sea",
            "Fire Sanctuary": "volcano_summit",
            "Thunderhead": "ballad_of_the_goddess",
            "Lanayru Gorge": "levias",
        },
        "region": {},
        "items": {
            # Rewards of a step: had once it's ticked.
            "items/sailcloth": "wing_ceremony",
            "items/slingshot": "faron_woods",
            "items/digging_mitts": "skyview_temple",
            "items/goddess_harp": "temple_of_time",
            "items/water_dragons_scale": "trial_farore",
            "items/clawshots": "trial_nayru",
            "items/fireshield_earrings": "trial_din",
            "items/seed_satchel": "faron_woods",
            "items/extra_bomb_bag": "key_pieces",
            "items/quiver": "lanayru_sand_sea",
            "items/pouch_fledge": "statue_of_the_goddess",
            "items/pouch_goddess_chest": "faron_woods",
            "items/bottle_luv": "statue_of_the_goddess",
            "items/bottle_fire_sanctuary": "volcano_summit",
            "shields/wooden_shield": "statue_of_the_goddess",
            "shields/iron_shield": None,
            "shields/sacred_shield": None,
            "shields/hylian_shield": "hero_song_lanayru",
            "side_quests/fledge_barrels": None,
            "side_quests/thrill_digger": "skyview_temple",
            "side_quests/bug_heaven": "levias",
            # The chandelier can be broken as soon as you can fly; only the last job needs the harp.
            "side_quests/pumm_chandelier_debt": "statue_of_the_goddess",
            "heart_pieces/lumpy_pumpkin_chandelier": "statue_of_the_goddess",
            "heart_pieces/pumm_chores_reward": "temple_of_time",
            "heart_pieces/faron_bombable_rock_cave": "key_pieces",
            "heart_pieces/goddess_chest_bug_rock": "trial_din",
            "heart_pieces/lightning_round_four_wins": "hero_song_lanayru",
            # The boss is inside the facility, like every other dungeon's container.
            "heart_pieces/container_moldarach": "power_generator",
            # Bombs are needed, and come from the Earth Temple.
            "heart_pieces/lanayru_secret_passage_chest": "earth_temple",
            "goddess_cubes/cube_skyview_spring": "faron_woods",
            "goddess_cubes/cube_lanayru_sand_oasis": "lanayru_mining_facility",
            "gratitude_crystals/peatrice_unwanted_admirer": "lanayru_mining_facility",
            # Bugs and treasures: where each is first caught or dropped. Skyloft's need nothing.
            "bugs_treasures/bug_skyloft_mantis": None,
            "bugs_treasures/bug_sky_stag_beetle": None,
            "bugs_treasures/bug_starry_firefly": None,
            "bugs_treasures/bug_blessed_butterfly": None,
            "bugs_treasures/bug_woodland_rhino_beetle": "statue_of_the_goddess",
            "bugs_treasures/bug_deku_hornet": "statue_of_the_goddess",
            "bugs_treasures/bug_faron_grasshopper": "statue_of_the_goddess",
            "bugs_treasures/bug_volcanic_ladybug": "skyview_temple",
            "bugs_treasures/bug_eldin_roller": "skyview_temple",
            "bugs_treasures/bug_lanayru_ant": "earth_temple",
            "bugs_treasures/bug_gerudo_dragonfly": "earth_temple",
            "bugs_treasures/bug_sand_cicada": "earth_temple",
            "bugs_treasures/treasure_amber_relic": None,
            "bugs_treasures/treasure_hornet_larvae": "statue_of_the_goddess",
            "bugs_treasures/treasure_jelly_blob": "statue_of_the_goddess",
            "bugs_treasures/treasure_bird_feather": "statue_of_the_goddess",
            "bugs_treasures/treasure_blue_bird_feather": "statue_of_the_goddess",
            # Keese fly over Skyloft at night from the start.
            "bugs_treasures/treasure_monster_claw": None,
            "bugs_treasures/treasure_ornamental_skull": "statue_of_the_goddess",
            "bugs_treasures/treasure_golden_skull": "statue_of_the_goddess",
            "bugs_treasures/treasure_eldin_ore": "skyview_temple",
            "bugs_treasures/treasure_lizard_tail": "key_pieces",
            "bugs_treasures/treasure_tumbleweed": "earth_temple",
            "bugs_treasures/treasure_ancient_flower": "earth_temple",
            "bugs_treasures/treasure_dusk_relic": "farores_courage",
            "bugs_treasures/treasure_evil_crystal": "skyview_temple",
            "bugs_treasures/treasure_monster_horn": "statue_of_the_goddess",
            "bugs_treasures/treasure_goddess_plume": "statue_of_the_goddess",
        },
    },
}


def checklists(game):
    """Every checklist of the game but its Main Quest, by file stem, from the files on disk."""
    return sorted(p for p in (FILES / game).glob("*.json") if p.stem != "main_quest" and "sections" in json.loads(p.read_text()))


def propose(game, stem, item):
    """(step or None, basis) for one item."""
    table = GAMES[game]
    key = f"{stem}/{item['id']}"
    if key in table["items"]:
        return table["items"][key], "override"
    for field in ("after", "area", "region"):
        text = item.get(field)
        if text is not None and text in table[field]:
            return table[field][text], field
    return None, "none"


def insert_link(text, item_id, steps):
    """`text` with `"unlockedBy": [...]` after the item's name, in the layout around it."""
    match = re.search(r'"id":\s*"' + re.escape(item_id) + r'"', text)
    if match is None:
        raise SystemExit(f"no item '{item_id}'")
    name = re.compile(r'"name":\s*"(?:[^"\\]|\\.)*",?').search(text, match.end())
    link = '"unlockedBy": [' + ", ".join(f'"{s}"' for s in steps) + "]"
    end = name.end()
    if text[end] == "\n":
        indent = text[text.rfind("\n", 0, name.start()) + 1:name.start()]
        return text[:end] + "\n" + indent + link + "," + text[end:]
    return text[:end] + " " + link + "," + text[end:]


def main(args):
    game = args[0]
    apply = "--apply" in args
    story = json.loads((FILES / game / "main_quest.json").read_text())
    steps = {it["id"] for s in story["sections"] for it in s["items"]}
    rows = []
    for path in checklists(game):
        catalog = json.loads(path.read_text())
        text = path.read_text()
        for section in catalog["sections"]:
            for item in section.get("items", []):
                step, basis = propose(game, path.stem, item)
                if step is not None and step not in steps:
                    raise SystemExit(f"{path.stem}/{item['id']}: '{step}' isn't a Main Quest step")
                rows.append((path.stem, item["id"], item["name"], item.get("after") or item.get("area") or "", step or "", basis))
                if apply and step is not None and "unlockedBy" not in item:
                    text = insert_link(text, item["id"], [step])
        if apply:
            path.write_text(text)
    print("file\tid\tname\tafter/area\tunlockedBy\tbasis")
    for row in rows:
        print("\t".join(row))


if __name__ == "__main__":
    main(sys.argv[1:])
