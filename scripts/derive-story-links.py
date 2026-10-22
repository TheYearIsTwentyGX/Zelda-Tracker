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

# Each game: `after`, `area` and `region` texts to a step id (None: available from the start);
# `items`, overrides by "<file stem>/<item id>"; optionally `inside`, a dungeon's region to the step
# that opens it, and `sticky`, "<file stem>/<section id>"s whose items inherit the gate above them.
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
    "tp": {
        # Each dungeon's region: the step that opens it.
        "inside": {
            "Forest Temple": "faron_twilight",
            "Goron Mines": "wrestle_bo",
            "Lakebed Temple": "rutela_gift",
            "Arbiter's Grounds": "gerudo_desert",
            "Snowpeak Ruins": "reekfish_scent",
            "Temple of Time": "snowpeak_ruins",
            "City in the Sky": "sky_characters",
            "Palace of Twilight": "city_in_the_sky",
            "Hyrule Castle": "palace_of_twilight",
        },
        # A dungeon named alone means after it; with an item, once you can get that item (inside it).
        "after": {
            "Faron Province restored": "faron_twilight",
            "Forest Temple": "forest_temple",
            "Eldin Twilight": "eldin_twilight",
            "Eldin Province's light restored (Epona back)": "eldin_twilight",
            "Goron Mines": "goron_mines",
            "After Goron Mines": "goron_mines",
            "After Goron Mines (Bombs from Barnes)": "goron_mines",
            "Lanayru Twilight": "lanayru_twilight",
            "Lanayru Province's light restored": "lanayru_twilight",
            "Lakebed Temple (Clawshot)": "rutela_gift",
            "Lakebed Temple": "lakebed_temple",
            "After Lakebed Temple": "lakebed_temple",
            "After Lakebed Temple, on your way to Zelda as a wolf": "lakebed_temple",
            "Master Sword": "draw_master_sword",
            "After the Master Sword": "draw_master_sword",
            "Lakebed Temple (Master Sword)": "draw_master_sword",
            # The Rupees for Kakariko's bridge are asked for after the Master Sword (Malo Mart's plan).
            "After paying 1,000 Rupees at Kakariko's Malo Mart to rebuild the east bridge": "draw_master_sword",
            "After the Goron Springwater Rush": "draw_master_sword",
            "After all 60 Poe Souls": "draw_master_sword",
            # Fyer's cannon flies you to the desert; the Bridge of Eldin's piece is warped home from it.
            "Lakebed Temple (Oasis Flight)": "gerudo_desert",
            "After reaching Gerudo Desert": "gerudo_desert",
            "After beating King Bulblin at the Bulblin Camp": "gerudo_desert",
            "After the Bridge of Eldin is restored": "gerudo_desert",
            "After the Bridge of Eldin is restored (from Gerudo Desert)": "gerudo_desert",
            "Gerudo Desert (Bridge of Eldin piece warped back)": "gerudo_desert",
            "Arbiter's Grounds (Spinner)": "gerudo_desert",
            "Arbiter's Grounds": "arbiters_grounds",
            "After Arbiter's Grounds": "arbiters_grounds",
            "Arbiter's Grounds (Ganondorf's execution scene)": "arbiters_grounds",
            "Arbiter's Grounds (Coral Earring)": "arbiters_grounds",
            "After getting the Reekfish Scent": "reekfish_scent",
            "Snowpeak Ruins (Ball and Chain)": "reekfish_scent",
            "Snowpeak Ruins": "snowpeak_ruins",
            "After Snowpeak Ruins": "snowpeak_ruins",
            "Snowpeak Ruins (Temple of Time opened)": "snowpeak_ruins",
            "Temple of Time": "temple_of_time",
            "After clearing the Hidden Village": "temple_of_time",
            "After restoring Ilia's memory": "ilia_memory",
            # Impaz hands over the Ancient Sky Book at the end of Ilia's memory; Shad restores the rod with it.
            "Once the Dominion Rod's power is restored": "ilia_memory",
            "Dominion Rod restored (Ancient Sky Book)": "ilia_memory",
            "All six Sky Characters": "sky_characters",
            "City in the Sky (Double Clawshots)": "sky_characters",
            "City in the Sky": "city_in_the_sky",
            "After City in the Sky": "city_in_the_sky",
            "Palace of Twilight (light-filled Master Sword)": "palace_of_twilight",
        },
        "area": {
            "Faron Woods": "faron_twilight",
            "Forest Temple": "faron_twilight",
            "Goron Mines": "wrestle_bo",
            "Arbiter's Grounds": "gerudo_desert",
            "Snowpeak Ruins": "reekfish_scent",
        },
        # Link is human in a province once its Twilight lifts.
        "region": {
            "Ordona Province": None,
            "Faron Province": "faron_twilight",
            "Eldin Province": "eldin_twilight",
            "Lanayru Province": "lanayru_twilight",
            "Gerudo Desert": "gerudo_desert",
            "Snowpeak": "reekfish_scent",
        },
        "sticky": {"cave_of_ordeals/floors", "cave_of_ordeals/cave_of_shadows"},
        "items": {
            "items/fishing_rod": None,
            "items/slingshot": None,
            "items/iron_boots": "wrestle_bo",
            "items/hawkeye": "goron_mines",
            "items/bombs": "goron_mines",
            "items/clawshot": "rutela_gift",
            "items/dominion_rod": "snowpeak_ruins",
            "items/horse_call": "ilia_memory",
            "items/sky_book": "ilia_memory",
            "items/ordon_sword": "ordon_sword_shield",
            "items/ordon_shield": "ordon_sword_shield",
            "items/master_sword": "draw_master_sword",
            # Malo Mart opens in Kakariko once Eldin is light again; the Hylian Shield once Colin is back.
            "items/wooden_shield": "eldin_twilight",
            "items/hylian_shield": "king_bulblin_bridge",
            "items/heros_clothes": "faron_twilight",
            "items/zora_armor": "rutela_gift",
            "items/magic_armor": "draw_master_sword",
            "items/wallet": "lanayru_twilight",
            "items/quiver": "rutela_gift",
            "items/bomb_bag_barnes": "goron_mines",
            "items/bomb_bag_goron": "rutela_gift",
            "items/bottle_sera": None,
            "items/bottle_jovani": "draw_master_sword",
            "items/ghost_lantern": "draw_master_sword",
            "items/colossal_wallet": "palace_of_twilight",
            # Wolf Link at will comes with the Master Sword's Shadow Crystal; before it only Jovani's own Poe.
            "poe_souls/jovani_20_souls": "draw_master_sword",
            "poe_souls/jovani_60_souls": "draw_master_sword",
            "poe_souls/gengle_rupees": "draw_master_sword",
            "poe_souls/cave_of_ordeals_33_poe": "reekfish_scent",
            "poe_souls/cave_of_ordeals_44_poe": "sky_characters",
            "heart_pieces/kakariko_gorge_cavern": "goron_mines",
            "heart_pieces/great_bridge_spinner_rails": "gerudo_desert",
            # Hidden Skills follow their Howling Stones; the trail's stone is past the Gorons.
            "hidden_skills/stone_death_mountain": "wrestle_bo",
            "hidden_skills/stone_snowpeak": "reekfish_scent",
            "hidden_skills/shield_attack": "wrestle_bo",
            "hidden_skills/helm_splitter": "lakebed_temple",
            "hidden_skills/jump_strike": "reekfish_scent",
            "hidden_skills/great_spin": "temple_of_time",
            "sidequests/hot_springwater": "draw_master_sword",
            "sidequests/castle_town_malo_mart": "draw_master_sword",
            "sidequests/letter_malo_mart": "draw_master_sword",
            "sidequests/letter_ooccoo_dear_adventurer_2": "forest_temple",
            "items/giant_bomb_bag": "lanayru_twilight",
            # Given once the other 49 are found; the last are in Hyrule Castle.
            "sidequests/stamp_twili_midna": "palace_of_twilight",
            "sidequests/fish_greengill": None,
            "sidequests/fish_ordon_catfish": None,
        },
    },
}


def checklists(game):
    """Every checklist of the game but its Main Quest, by file stem, from the files on disk."""
    return sorted(p for p in (FILES / game).glob("*.json") if p.stem != "main_quest" and "sections" in json.loads(p.read_text()))


def story_depth(story):
    """Each step by how far into the story it comes, as `storyOrder` in StoryGate.kt works it out."""
    by_id = {it["id"]: it for s in story["sections"] for it in s["items"]}
    depth = {}

    def visit(step_id):
        if step_id not in depth:
            depth[step_id] = 0
            depth[step_id] = max((visit(r) + 1 for r in by_id[step_id].get("requires", []) if r in by_id), default=0)
        return depth[step_id]

    for step_id in by_id:
        visit(step_id)
    return depth


def propose(game, stem, item, depth):
    """
    (step or None, basis) for one item. An item in a dungeon's region (the table's `inside`) is
    gated by the step that opens the dungeon, unless its `after` needs something later.
    """
    table = GAMES[game]
    key = f"{stem}/{item['id']}"
    if key in table["items"]:
        return table["items"][key], "override"
    found = None, "none"
    for field in ("after", "area", "region"):
        text = item.get(field)
        if text is not None and text in table.get(field, {}):
            found = table[field][text], field
            break
    inside = table.get("inside", {}).get(item.get("region"))
    if inside is not None and (found[0] is None or depth[inside] >= depth[found[0]]):
        return inside, "inside"
    return found


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
    depth = story_depth(story)
    sticky = GAMES[game].get("sticky", set())
    rows = []
    for path in checklists(game):
        catalog = json.loads(path.read_text())
        text = path.read_text()
        for section in catalog["sections"]:
            # In a `sticky` section (floors of a cave, say) an item with nothing of its own is gated like the one before.
            last = None
            for item in section.get("items", []):
                step, basis = propose(game, path.stem, item, depth)
                if f"{path.stem}/{section['id']}" in sticky:
                    if basis == "none" and last is not None:
                        step, basis = last, "sticky"
                    last = step
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
