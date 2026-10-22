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

# Ocarina of Time's places, by the step that opens them; the dungeons by the step before them.
OOT_PLACES = {
    **{place: None for place in (
        "Kokiri Forest", "Lost Woods", "Sacred Forest Meadow", "Hyrule Field", "Hyrule Market", "Hyrule Castle",
        "Lon Lon Ranch", "Kakariko Village", "Kakariko Graveyard", "Zora's River", "Lake Hylia", "Gerudo Valley",
        "Inside the Deku Tree",
    )},
    # Zelda's Letter opens the trail; her Lullaby opens Zora's Domain.
    **{place: "meet_zelda" for place in (
        "Death Mountain Trail", "Goron City", "Death Mountain Crater", "Zora's Domain", "Zora's Fountain",
        "Dodongo's Cavern", "Inside Jabu-Jabu's Belly",
    )},
    **{place: "master_sword" for place in ("Gerudo's Fortress", "Outside Ganon's Castle", "Temple of Time")},
    **{place: "light_medallion" for place in ("Forest Temple", "Fire Temple", "Ice Cavern", "Bottom of the Well")},
    "Water Temple": "ice_cavern",
    "Shadow Temple": "bottom_of_the_well",
    **{place: "gerudo_fortress" for place in ("Spirit Temple", "Haunted Wasteland", "Desert Colossus", "Gerudo Training Ground")},
}

# Oracle of Seasons' eight dungeons: what the Huge Maku Seed needs.
OOS_DUNGEONS = (
    "gnarled_root_dungeon", "snakes_remains", "poison_moths_lair", "dancing_dragon_dungeon", "unicorns_cave",
    "ancient_ruins", "explorers_crypt", "sword_and_shield_maze",
)

# Oracle of Ages' eight dungeons: what the Huge Maku Seed needs.
OOA_DUNGEONS = (
    "spirits_grave", "wing_dungeon", "moonlit_grotto", "skull_dungeon", "crown_dungeon", "mermaids_cave",
    "jabu_jabus_belly", "ancient_tomb",
)

# Echoes of Wisdom: the three small rifts that open the desert warriors' rift, and the three temples
# of the Lands of the Goddesses, which open the Eternal Forest.
EOW_DESERT_RIFTS = ("southern_oasis_ruins_rift", "ancestors_cave_rift", "southern_gerudo_desert_rift")
EOW_GODDESSES = ("eldin_temple", "lanayru_temple", "faron_temple")

# Majora's Mask's four temples: what the Moon needs.
MM_TEMPLES = ("woodfall_temple", "snowhead_temple", "great_bay_temple", "stone_tower_temple")

# The five temples after the Light Medallion, in any order: what opens Ganon's Castle.
OOT_TEMPLES = ("forest_temple", "fire_temple", "water_temple", "shadow_temple", "spirit_temple")

# Each game: `after`, `area` and `region` texts to a step id (None: available from the start);
# `items`, overrides by "<file stem>/<item id>"; optionally `inside`, a dungeon's region to the step
# that opens it, `sticky`, "<file stem>/<section id>"s whose items inherit the gate above them, and
# `needs`, `places` and `photo`, for a game whose items say in their text what they need (see propose),
# `tags`, an item's tags to a step, `sections`, a step every item of a "<file stem>/<section id>"
# needs at least, and `refs`, a regex whose groups name another item (by an id
# template) whose gate it shares. A step may be a tuple of steps that must all be done.
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
    "ww": {
        # The sea opens with the Wind Waker (`dragon_roost_island`); after that, what an item needs is
        # mostly gear, which its text names. Outset Island is reached again only by sailing.
        "after": {
            "After getting the Wind Waker": "dragon_roost_island",
            "After Dragon Roost Cavern": "dragon_roost_cavern",
            "After Farore's Pearl": "farores_pearl",
            "After the Forbidden Woods": "forbidden_woods",
            "After the second visit to the Forsaken Fortress": "forsaken_fortress_second",
        },
        "region": {
            "Windfall Island": "windfall_sail",
            "Forest Haven": "dins_pearl",
            "Tower of the Gods": "nayrus_pearl",
            "Forsaken Fortress": "hyrule_castle",
        },
        # Where something is: a dungeon by the step that opens it, any other island by the sea.
        "places": {
            r"Windfall": "windfall_sail",
            r"Dragon Roost|Flight Control Platform": "dragon_roost_island",
            r"Forest Haven": "dins_pearl",
            r"Forbidden Woods": "forest_haven",
            r"Tower of the Gods|Temple of the Gods": "nayrus_pearl",
            r"Hyrule Castle": "tower_of_the_gods",
            r"Forsaken Fortress": "hyrule_castle",
            r"Earth Temple|Wind Temple": "forsaken_fortress_second",
            r"Ganon's Tower": "triforce_of_courage",
            r"Great Sea|(?<!Windfall )Island|Isles?\b|Reef|Archipelago|Boating Course|Bird's Peak Rock": "dragon_roost_island",
        },
        "needs": {
            r"Grappling Hook|Wind's Requiem|Wind Waker|Song of Passing": "dragon_roost_island",
            r"Hyoi Pear|seagull|All-Purpose Bait|Bait Bag|Beedle": "dragon_roost_island",
            r"Deku Leaf|[Gg]lide|Boomerang|Magic Meter": "forest_haven",
            r"\bBombs?\b(?! Shop| Island)|[Bb]low up|[Bb]omb (it|the)|cannon|[Ss]ink the|warships?": "windfall_bombs",
            r"\barrows?\b|Hero's Bow|Cyclos|Ballad of Gales|Command Melody|Darknuts?|Knight's Crest": "nayrus_pearl",
            r"Skull Hammer|Master Sword": "hyrule_castle",
            r"Fire Arrow|Ice Arrow|Hookshot|Power Bracelets|stone head|Iron Boots|Mirror Shield": "forsaken_fortress_second",
            r"Ghost Ship Chart|Aryll is rescued|Skull Necklace": "forsaken_fortress_second",
            r"Light Arrow": "triforce_of_courage",
            r"colour pictograph|Deluxe Picto Box": "dins_pearl",
            r"Picto Box|Tingle(?! Island)": "windfall_sail",
        },
        "refs": {r"Treasure Chart (\d+)": "treasure_chart_{}"},
        # A figurine needs a colour pictograph (the Deluxe Picto Box, with a Forest Firefly) shown to
        # Carlov in the Nintendo Gallery by Forest Haven (opened with a Hyoi Pear): `dins_pearl` at the
        # earliest, then the first place its subject can be photographed.
        "photo": {"figurines/figurines": "dins_pearl"},
        "items": {
            # Had on Outset and Tetra's ship, before the sea opens.
            "items/sword": "outset_island",
            "items/shield": "outset_island",
            "items/telescope": "outset_island",
            "items/pirates_charm": "outset_island",
            "items/spoils_bag": "outset_island",
            "items/sail": "windfall_sail",
            # Charted already when the King of Red Lions hands over the Sea Chart.
            "sea_chart/a1_forsaken_fortress": "windfall_sail",
            "sea_chart/b4_windfall_island": "windfall_sail",
            "sea_chart/g2_outset_island": "windfall_sail",
            "items/wind_waker": "dragon_roost_island",
            "items/delivery_bag": "dragon_roost_island",
            "items/bottle_medli": "dragon_roost_island",
            "items/deku_leaf": "forest_haven",
            "items/bombs": "windfall_bombs",
            "items/magic_armor": "dragon_roost_island",
            "items/heros_charm": "windfall_sail",
            # Each upgrade is from either of two Great Fairies: the earlier one counts.
            "items/wallet": "dragon_roost_island",
            "items/bomb_bag": "windfall_bombs",
            "items/quiver": "hyrule_castle",
            "great_fairies/southern_fairy_island": "windfall_bombs",
            # The FF1 jail cell is open to a swordless Link too.
            "heart_pieces/forsaken_fortress_jail": "outset_island",
            "heart_pieces/withered_trees": "forbidden_woods",
            # The islet's chest opens once the lighthouse is lit, with a Fire Arrow from the windmill.
            "heart_pieces/windfall_islet_chest": "forsaken_fortress_second",
            "heart_pieces/pawprint_chuchu_cave": "dragon_roost_island",
            "sea_secrets/pawprint_chuchu_cave": "dragon_roost_island",
            # A Big Octo's eyes fall to the Boomerang as well as to arrows or the cannon.
            "heart_pieces/tingle_island_big_octo": "forest_haven",
            "heart_pieces/seven_star_big_octo": "forest_haven",
            "great_fairies/two_eye_reef": "forest_haven",
            "sea_secrets/seven_star_isles_big_octo": "forest_haven",
            "sea_secrets/tingle_island_big_octo": "forest_haven",
            "sea_secrets/fire_mountain_big_octo": "forest_haven",
            "sea_secrets/private_oasis_big_octo": "forest_haven",
            "sea_secrets/diamond_steppe_island_big_octo": "forest_haven",
            "sea_secrets/two_eye_reef_big_octo": "forest_haven",
            "sea_secrets/rock_spire_isle_cave": "windfall_bombs",
            "sea_secrets/horseshoe_island_cave": "forest_haven",
            "sea_secrets/boating_course_cave": "forest_haven",
            "charts/submarine_chart": "forest_haven",
            "charts/platform_chart": "dragon_roost_island",
            "side_quests/tingle_statues_reward": "forsaken_fortress_second",
            # Legendary Pictographs are sold once Aryll is rescued; Zelda comes with the King's.
            "figurines/fado": "forsaken_fortress_second",
            "figurines/laruto": "forsaken_fortress_second",
            "figurines/ganondorf": "forsaken_fortress_second",
            "figurines/jabun": "forsaken_fortress_second",
            "figurines/great_fairy": "forsaken_fortress_second",
            "figurines/king_of_hyrule": "forsaken_fortress_second",
            "figurines/princess_zelda": "forsaken_fortress_second",
            "figurines/queen_of_fairies": "forsaken_fortress_second",
            # Gohma is met before the Deluxe Picto Box; only her rematch in Ganon's Tower can be photographed.
            # Tetra's crew come with her figurine; she can be photographed in Hyrule Castle. Niko also on
            # her ship, when Link sneaks aboard for the Bombs.
            "figurines/gonzo": "tower_of_the_gods",
            "figurines/mako": "tower_of_the_gods",
            "figurines/nudge": "tower_of_the_gods",
            "figurines/senza": "tower_of_the_gods",
            "figurines/zuko": "tower_of_the_gods",
            "figurines/niko": "windfall_bombs",
            # The sea around a dungeon is open like the rest of it.
            "sea_chart/d5_tower_of_the_gods": "dragon_roost_cavern",
            "sea_chart/f6_forest_haven": "dragon_roost_cavern",
            "figurines/gohma": "triforce_of_courage",
            "figurines/link_and_the_king_of_red_lions": "triforce_of_courage",
        },
    },
    "oos": {
        # Horon Village and the Western Coast are open from the start, the rest of Holodrum once Link
        # has a sword, and each further place with the dungeon item or animal that reaches it. A
        # dungeon is gated by the step before it; a dungeon named alone in `after` means after it.
        "places": {
            r"North Horon|Eastern Suburbs|Holodrum Plain|Anywhere in Holodrum": "heros_cave",
            r"Gnarled Root Dungeon": "maku_tree",
            r"Woods of Winter|Subrosia|Temple of Seasons|Snake's Remains": "gnarled_root_dungeon",
            r"Spool Swamp|Poison Moth's Lair": "snakes_remains",
            r"Natzu|Sunken City|Mt\. Cucco|Goron Mountain|Eyeglass Lake|Samasa Desert|Dancing Dragon|Unicorn's Cave": "poison_moths_lair",
            r"Tarm Ruins|Lost Woods|Ancient Ruins": "unicorns_cave",
            r"Graveyard|Explorer's Crypt": "ancient_ruins",
            r"Temple Remains|Sword & Shield Maze": "explorers_crypt",
        },
        # Dungeon items named in the text, by the step before their dungeon.
        "needs": {
            r"Boomerang": "gnarled_root_dungeon",
            r"Slingshot|Magnetic Gloves": "poison_moths_lair",
            r"Piratians": "ancient_ruins",
            r"Hyper Slingshot": "explorers_crypt",
        },
        "tags": {
            "Ember Seeds": "maku_tree",
            "Bombs": "maku_tree",
            "Power Bracelet": "gnarled_root_dungeon",
            "Rod of Seasons (Winter)": "gnarled_root_dungeon",
            "Ore Chunks": "gnarled_root_dungeon",
            "Roc's Feather": "snakes_remains",
            "Rod of Seasons (Summer)": "snakes_remains",
            "Animal companion": "snakes_remains",
            "Rod of Seasons (Spring)": "poison_moths_lair",
            "Rod of Seasons (Autumn)": "poison_moths_lair",
            "Zora's Flippers": "poison_moths_lair",
            "Dimitri or Zora's Flippers": "poison_moths_lair",
            "Zora's Flippers or Dimitri": "poison_moths_lair",
            "Roc's Cape": "ancient_ruins",
        },
        "after": {
            "First meeting the Maku Tree": "maku_tree",
            "Gnarled Root Dungeon": "gnarled_root_dungeon",
            "Reaching Subrosia": "gnarled_root_dungeon",
            "Power of Winter": "gnarled_root_dungeon",
            "Snake's Remains": "snakes_remains",
            "Pegasus Seeds": "snakes_remains",
            "Poison Moth's Lair (Roc's Feather)": "snakes_remains",
            "Poison Moth's Lair": "poison_moths_lair",
            "Power of Spring": "poison_moths_lair",
            "Power of Autumn": "poison_moths_lair",
            "Destroying Moblin's Keep": "poison_moths_lair",
            "Curing Biggoron's cold": "poison_moths_lair",
            "Dancing Dragon Dungeon": "dancing_dragon_dungeon",
            "Unicorn's Cave": "unicorns_cave",
            "Ancient Ruins": "ancient_ruins",
            "Helping the Piratians": "ancient_ruins",
            "Explorer's Crypt": "explorers_crypt",
            "Volcano eruption": "explorers_crypt",
            "The end of a linked game": "ganon",
            # Secrets heard in a linked Ages game come from that game's story, not this one.
            **{text: None for text in (
                "Spirit's Grave (in Ages)", "Wing Dungeon (in Ages)", "Wing Dungeon (Roc's Feather) (in Ages)",
                "Skull Dungeon (in Ages)", "Crown Dungeon (in Ages)", "Mermaid's Cave (in Ages)",
            )},
        },
        "items": {
            "items/ember_seeds": "maku_tree",
            "items/rod_of_seasons": "gnarled_root_dungeon",
            "items/strange_flute": "snakes_remains",
            "items/scent_seeds": "maku_tree",
            "items/sword": "heros_cave",
            "items/gnarled_key": "maku_tree",
            "items/huge_maku_seed": OOS_DUNGEONS,
            "linked_secrets/secret_to_labrynna": "onoxs_castle",
        },
    },
    "la": {
        # Mabe Village and its shore are open from the start; each further part of Koholint opens with
        # the dungeon item that reaches it. A dungeon is gated by the step before it (the one that
        # gives its key); a dungeon named alone in `after` means after it.
        "places": {
            r"Mysterious Forest|Ukuku Prairie|Koholint Prairie|Pothole Field|Tabahl Wasteland|Color Dungeon": "find_sword",
            r"Tail Cave": "tail_key",
            r"Goponga Swamp|Bottle Grotto": "rescue_bowwow",
            r"Kanalet Castle": "bottle_grotto",
            r"Key Cavern": "slime_key",
            r"Animal Village": "dream_shrine",
            r"Yarna Desert": "walrus",
            r"Tal Tal Heights|Martha's Bay|Rapids Ride|Angler's Tunnel": "angler_key",
            r"Tal Tal Mountain Range|Catfish's Maw": "anglers_tunnel",
            r"Ancient Ruins": "catfishs_maw",
            r"Face Shrine": "face_key",
            r"Eagle's Tower": "bird_key",
            r"Turtle Rock": "turtle_rock_head",
        },
        # Each dungeon's item, by the step before that dungeon.
        "tags": {
            "Sword": "find_sword",
            "Roc's Feather": "tail_key",
            "Power Bracelet": "rescue_bowwow",
            "Golden Leaves": "bottle_grotto",
            "Pegasus Boots": "slime_key",
            "Ocarina": "dream_shrine",
            "Flippers": "angler_key",
            "Hookshot": "anglers_tunnel",
            "Power Bracelet L-2": "face_key",
            "Flying Rooster": "flying_rooster",
            "Mirror Shield": "bird_key",
        },
        "after": {
            "Tail Cave (Roc's Feather)": "tail_key",
            "Bottle Grotto (Power Bracelet)": "rescue_bowwow",
            "BowWow back home (Zelda Wiki)": "bottle_grotto",
            "Kiki's bridge to Kanalet Castle (trade the Bananas)": "bottle_grotto",
            "Key Cavern (Pegasus Boots)": "slime_key",
            "Key Cavern": "key_cavern",
            "Angler's Tunnel (Flippers)": "angler_key",
            "Angler's Tunnel": "anglers_tunnel",
            "Catfish's Maw (Hookshot)": "anglers_tunnel",
            "Catfish's Maw": "catfishs_maw",
            "Face Shrine (the Flying Rooster)": "face_shrine",
            "Turtle Rock": "turtle_rock",
        },
        "needs": {
            r"Magic Powder": "find_sword",
            r"Marin follows you|with Marin": "dream_shrine",
            r"Hookshot|Magnifying Lens": "anglers_tunnel",
        },
        # The Camera Shop is outside Mabe Village.
        "sections": {"photographs/photos": "find_sword"},
        "items": {
            "items/ocarina": "dream_shrine",
            "items/secret_medicine": "find_sword",
            "songs/frogs_song_of_soul": "find_sword",
            # The Ghost follows Link out of Angler's Tunnel.
            "items/switch_bottle_ghost": "anglers_tunnel",
            "photographs/ghost_grave": "anglers_tunnel",
        },
    },
    "ooa": {
        # Lynna is open from the start, the past from the first time portal (`nayru_possessed`), and the
        # rest of Labrynna with the dungeon item, animal or tune that reaches it. A dungeon is gated by
        # the step before it; a dungeon named alone in `after` means after it.
        "places": {
            r"Black Tower \(past\)|Ambi's Palace": "nayru_possessed",
            r"Yoll Graveyard|Deku Forest|Spirit's Grave": "maku_tree_rescue",
            r"Fairies' Woods|Wing Dungeon": "spirits_grave",
            r"Talus Peaks|Crescent Island|Moonlit Grotto": "wing_dungeon",
            r"Symmetry|Nuun Highlands|Skull Dungeon": "moonlit_grotto",
            r"Rolling Ridge|Crown Dungeon": "skull_dungeon",
            r"Mermaid's Cave": "crown_dungeon",
            r"Zora Village|Zora Seas|Eyeglass Library|Jabu-Jabu's Belly": "rescue_nayru",
            r"Sea of Storms|Sea of No Return|Ancient Tomb": "jabu_jabus_belly",
        },
        "needs": {
            r"Seed Shooter": "wing_dungeon",
            r"Switch Hook": "moonlit_grotto",
            r"Cane of Somaria": "skull_dungeon",
            r"Long Hook": "rescue_nayru",
        },
        "tags": {
            "Past": "nayru_possessed",
            "Shovel": "nayru_possessed",
            "Bombs": "nayru_possessed",
            "Ember Seeds": "maku_tree_rescue",
            "Power Bracelet": "maku_tree_rescue",
            "Roc's Feather": "spirits_grave",
            "Zora's Flippers": "wing_dungeon",
            "Animal companion": "wing_dungeon",
            "Tune of Currents": "moonlit_grotto",
            "Dimitri or Mermaid Suit": "moonlit_grotto",
            "Mermaid Suit": "crown_dungeon",
            "Tune of Ages": "mermaids_cave",
            "Zora Scale": "jabu_jabus_belly",
        },
        "after": {
            "Shovel": "nayru_possessed",
            "Ember Seeds": "maku_tree_rescue",
            "Spirit's Grave": "spirits_grave",
            "Wing Dungeon (Roc's Feather)": "spirits_grave",
            "Wing Dungeon": "wing_dungeon",
            "Moonlit Grotto": "moonlit_grotto",
            "Tune of Currents": "moonlit_grotto",
            "Skull Dungeon": "skull_dungeon",
            "Crown Dungeon": "crown_dungeon",
            "Mermaid's Cave": "mermaids_cave",
            "The end of a linked game": "ganon",
            # Secrets heard in a linked Seasons game come from that game's story, not this one.
            **{text: None for text in (
                "Poison Moth's Lair (in Seasons)", "First meeting the Maku Tree (in Seasons)", "Snake's Remains (in Seasons)",
                "Power of Winter (in Seasons)", "Poison Moth's Lair (Roc's Feather) (in Seasons)",
                "Destroying Moblin's Keep (in Seasons)", "Power of Autumn (in Seasons)", "Helping the Piratians (in Seasons)",
                "Curing Biggoron's cold (in Seasons)",
            )},
        },
        "items": {
            "items/sword": "nayru_possessed",
            "items/seed_satchel": "maku_tree_rescue",
            "items/ember_seeds": "maku_tree_rescue",
            "items/zora_flippers": "wing_dungeon",
            "items/ricky_gloves": "wing_dungeon",
            "items/island_chart": "wing_dungeon",
            "items/huge_maku_seed": OOA_DUNGEONS,
            "harp_songs/tune_of_echoes": "spirits_grave",
            "harp_songs/tune_of_ages": "mermaids_cave",
            "linked_secrets/secret_to_holodrum": "black_tower",
        },
    },
    "st": {
        # Aboda and Castle Town are open from the start; the rest of each realm comes with its rail map
        # (the Ocean Realm with the rebuilt bridge, the Sand Realm with Anjean's Force Gem). A temple is
        # gated by the duet that opens it; tags name the gear an item needs. A Force Gem named in an
        # item's `after` is gated as that gem is.
        "region": {
            "Snow Realm": "tower_snow_rail_map",
            "Ocean Realm": "trading_post_bridge",
            "Fire Realm": "tower_fire_rail_map",
            "Sand Realm": "tower_byrne",
            "Tower of Spirits": "tunnel_to_the_tower",
        },
        "places": {
            r"Whittleton|Lost Woods|Forest Sanctuary|Trading Post": "tower_forest_rail_map",
            r"Rabbitland": "tower_snow_rail_map",
            r"Forest Temple": "gage_duet",
            r"Snow Temple": "steem_duet",
            r"Ocean Temple": "carben_duet",
            r"Fire Temple": "fire_temple_keys",
            r"Sand Temple": "three_trials",
            r"Pirate Hideout": "fire_temple",
        },
        "needs": {r"with the Cannon": "alfonzo_cannon"},
        "tags": {
            "Spirit Flute": "recruits_sword",
            "Whirlwind": "gage_duet",
            "Song of Discovery": "tower_snow_rail_map",
            "Boomerang": "steem_duet",
            "Song of Birds": "trading_post_bridge",
            "Whip": "carben_duet",
            "Bow": "fire_temple_keys",
            "Sand Wand": "three_trials",
            "Bombs": "snow_temple",
        },
        "after": {
            "Forest Temple": "forest_temple",
            "Snow Rail Map": "tower_snow_rail_map",
            "Rabbit Net": "tower_snow_rail_map",
            "Steem's duet": "steem_duet",
            "Snow Temple": "snow_temple",
            "Ocean Rail Map": "tower_ocean_rail_map",
            "Trading Post bridge quest begun (Kenzo)": "tower_ocean_rail_map",
            "Carben's duet": "carben_duet",
            "Ocean Temple (Whip)": "carben_duet",
            "Ocean Temple": "ocean_temple",
            "Fire Rail Map": "tower_fire_rail_map",
            "Freight car": "kagoron_mega_ice",
            "Lava cooled in Goron Village": "kagoron_mega_ice",
            "Embrose's duet": "embrose_duet",
            "Fire Temple": "fire_temple",
            "Rael's duet": "rael_duet",
            "First trial": "rael_duet",
            "Second trial": "rael_duet",
            "Sand Temple": "sand_temple",
            # Force Gems, by the gate of the gem itself (force_gems.json).
            "Noko's Force Gem": "tower_snow_rail_map",
            "Carben's Force Gem": "trading_post_bridge",
            "Mona's Force Gem": "ocean_temple",
            "Orca's Force Gem": "ocean_temple",
            "Yeko's Force Gem": "kagoron_mega_ice",
            "Steem's Force Gem": "kagoron_mega_ice",
            "Harry's Force Gem": "kagoron_mega_ice",
            "Lucia's Force Gem": "kagoron_mega_ice",
            "Force Gem: Goron's lava-blocked house": "kagoron_mega_ice",
            "Wadatsumi's Force Gem": "fire_temple_keys",
            "Niboshi's Force Gem": "fire_temple",
            "Niboshi's Force Gem (Mega Ice to Papuchia Village)": "fire_temple",
            "Force Gem: Goron child in Castle Town": "fire_temple",
            "Force Gem: Goron visits Anouki Village": "fire_temple",
            "Anjean's Force Gem": "tower_byrne",
            "Rael's Force Gem": "sand_temple",
            "Rael's Force Gem (five Cuccos to the Sand Sanctuary)": "sand_temple",
            "Force Gem: Ferrus on the Ocean Floor": "sand_temple",
            "Linebeck's Force Gem": "sand_temple",
        },
        "sections": {
            # Rabbits are caught with the Rabbit Net after their rock is shot with the Cannon.
            "rabbits/rabbits": "alfonzo_cannon",
            "rabbits/bunnio_rewards": "alfonzo_cannon",
            # Linebeck III trades train parts once the bridge is rebuilt.
            **{
                f"train_parts/{train}": "trading_post_bridge"
                for train in ("wooden_train", "steel_train", "skull_train", "stagecoach_train", "dragonhead_train",
                              "sweet_train", "golden_train")
            },
            "stamps/stamps": "alfonzo_cannon",
            "stamps/stamp_rewards": "alfonzo_cannon",
        },
        "items": {
            "items/sword": "recruits_sword",
            "items/spirit_flute": "recruits_sword",
            "items/shield": None,
            "items/stamp_book": "alfonzo_cannon",
            "items/shield_of_antiquity": "alfonzo_cannon",
            "items/engineers_clothes": "alfonzo_cannon",
            "items/rabbit_net": "tower_snow_rail_map",
            "items/bomb_bag": "snow_temple",
            "items/quiver": "fire_temple_keys",
            # All 20 stamps and all 50 rabbits take in the Sand Realm's.
            "items/great_spin_attack": "tower_byrne",
            "items/sword_beam": "tower_byrne",
            "stamps/niko_twenty_stamps": "tower_byrne",
            "songs/song_of_awakening": "gage_duet",
            "songs/song_of_healing": "gage_duet",
            "songs/song_of_light": "tower_ocean_rail_map",
            "heart_containers/beedle_heart": "snow_temple",
            "stations/station_castle_town": None,
            "stations/station_goron_target_range": "fire_temple",
            "train_parts/spirit_engine": "tower_forest_rail_map",
            "train_parts/practical_cannon": "alfonzo_cannon",
            "train_parts/solid_passenger_car": "tower_forest_rail_map",
            "train_parts/trusty_freight_car": "kagoron_mega_ice",
            "stations/station_whittleton": "tower_forest_rail_map",
            "stations/station_trading_post": "tower_forest_rail_map",
            "stations/station_rabbitland_rescue": "tower_snow_rail_map",
            "deliveries/ride_alfonzo": "tower_snow_rail_map",
            "deliveries/ride_ferrus_ocean_temple": "sand_temple",
        },
    },
    "ph": {
        # Mercay is open from the start; the ship comes with Linebeck (`totok_1f`). Each sea opens
        # with its chart, but the Northwestern Sea's storm needs the fog route from Molida. A dungeon
        # is gated by the step that opens it; tags and text name the gear an item needs.
        "region": {
            "Southwestern Sea": "totok_1f",
            "Northwestern Sea": "molida_fog_route",
            "Southeastern Sea": "totok_b7_b9",
            "Northeastern Sea": "totok_b10_b13",
        },
        "places": {
            r"Temple of the Ocean King": "oshus_sword",
            r"Isle of Ember|Temple of Fire|Cannon Island|Beedle's Shop Ship|Masked Ship": "totok_1f",
            r"Molida Island|Spirit Island": "eddos_cannon",
            r"Temple of Wind|Isle of Gust|Bannan Island|Zauz's Island|Uncharted Island": "molida_fog_route",
            r"Temple of Courage": "salvage_sun_key",
            r"Ghost Ship": "temple_of_courage",
            r"Goron Temple": "totok_b7_b9",
            r"Temple of Ice": "goron_temple",
            r"Isle of the Dead": "totok_b10_b13",
            r"Mutoh's Temple|Isle of Ruins": "isle_of_the_dead",
        },
        "needs": {
            r"with the Cannon": "eddos_cannon",
            r"\bSalvage it\b": "buy_salvage_arm",
        },
        # Each dungeon's item: the Shovel is found on Molida, the Bombs in the Temple of Wind, and so on.
        "tags": {
            "Shovel": "eddos_cannon",
            "Bombs": "molida_fog_route",
            "Bow": "salvage_sun_key",
            "Bombchus": "totok_b7_b9",
            "Grappling Hook": "goron_temple",
            "Hammer": "isle_of_the_dead",
            "Fish": "molida_fog_route",
        },
        "after": {
            "After the Temple of Fire": "temple_of_fire",
            "After the Temple of Wind": "temple_of_wind",
            "After the Temple of the Ocean King's B6": "totok_b4_b6",
            "After the Sun Key": "salvage_sun_key",
            "After the Temple of Courage": "temple_of_courage",
            "After the Ghost Ship": "ghost_ship",
            "After the Southeastern Sea Chart": "totok_b7_b9",
            "After the Goron Temple": "goron_temple",
            "After the Temple of Ice": "temple_of_ice",
            "After the Northeastern Sea Chart": "totok_b10_b13",
            "After lowering the water": "isle_of_the_dead",
            "After Mutoh's Temple": "mutohs_temple",
            "After showing the Old Wayfarer a Rusty Swordfish": "molida_fog_route",
        },
        # Ship parts and treasures come at random from chests, salvage and shops, once Link has a ship.
        "sections": {
            **{
                f"ship_parts/{part}": "totok_1f"
                for part in ("bright_ship", "iron_ship", "stone_ship", "vintage_ship", "demon_ship", "tropical_ship",
                             "dignified_ship", "golden_ship", "treasure")
            },
            # A golden frog's warp needs the Cyclone Slate, from Uncharted Island.
            "sea_chart/golden_frogs": "molida_fog_route",
        },
        "items": {
            "items/sword": "oshus_sword",
            "items/wooden_shield": None,
            "spirit_gems/power_mercay_shop": None,
            "sea_chart/island_mercay": None,
            "items/phantom_hourglass": "temple_of_fire",
            "items/cannon": "totok_b1_b3",
            "items/salvage_arm": "totok_b4_b6",
            "items/fishing_rod": "molida_fog_route",
            "sidequests/fishing_rod_joanne": "molida_fog_route",
            "heart_containers/island_shop_heart": "goron_temple",
            "heart_containers/shooting_range_heart": "temple_of_courage",
            "sidequests/shooting_range": "temple_of_courage",
            "sidequests/goron_game": "temple_of_ice",
            # The Temple of the Ocean King's floors open with the doors each spirit unlocks: B4–B6
            # after the Temple of Wind, B7–B9 after the Ghost Ship (the others' gear already says so).
            "spirit_gems/power_totok_b4": "temple_of_wind",
            "spirit_gems/courage_totok_b8": "ghost_ship",
            "spirit_gems/wisdom_totok_b9": "ghost_ship",
        },
    },
    "eow": {
        # Zelda wakes in Suthorn after escaping the castle; once the Suthorn Ruins are cleared all of
        # Hyrule is open to explore, though each region's rifts wait for the story. So places outside
        # Suthorn are gated by `suthorn_ruins`, and a dungeon or Still World by the step that opens it.
        "region": {
            "Suthorn Prairie": "escape_castle",
            "Hyrule Castle": "suthorn_ruins",
            "Hyrule Field": "suthorn_ruins",
            "Eastern Hyrule Field": "suthorn_ruins",
            "Gerudo Desert": "suthorn_ruins",
            "Jabul Waters": "suthorn_ruins",
            "Lake Hylia": "suthorn_ruins",
            "Eldin Volcano": "suthorn_ruins",
            "Hebra Mountain": "suthorn_ruins",
            "Faron Wetlands": "suthorn_ruins",
            "Eternal Forest": EOW_GODDESSES,
        },
        "places": {
            r"Hyrule Castle Underground": None,
            r"Suthorn Ruins": "stilled_suthorn_forest",
            r"Stilled Suthorn": "escape_castle",
            r"Ancestor's Cave of Rest": "gerudo_town_visit",
            r"Cryptic Cavern": "stilled_desert_temple_ruins",
            r"Gerudo Sanctum": "cryptic_cavern",
            r"Stilled Desert Temple Ruins": EOW_DESERT_RIFTS,
            r"Stilled Upper Zora River": "jabul_waters_rift_start",
            r"Jabul Ruins|Stilled Jabul Waters": "lord_jabu_jabus_den",
            r"Hyrule Castle \((dungeon|Still)|Stilled Hyrule Castle": "still_missing_castle_town",
            r"Stilled Goron City|Rock-Roast Quarry|Lizalfos Burrow": "hyrule_castle",
            r"Eldin Temple|Stilled Eldin": "eldin_crater_shortcut",
            r"Stilled Hebra Mountain": "hebra_mountain_passage",
            r"Lanayru Temple|Stilled Holy Mount": "holy_mount_lanayru_ascent",
            r"Stilled Heart Lake|Stilled Faron|The Sweet Spot|Stilled Northern Sanctuary": "hyrule_castle",
            r"Faron Temple": "faron_sweet_spot",
            r"Eternal Forest|Ancient Ruins": EOW_GODDESSES,
            r"Null's Body": "stilled_ancient_ruins",
        },
        "after": {
            "Prologue": None,
            "The Mysterious Rifts": "escape_castle",
            "After The Mysterious Rifts": "lueburrys_house",
            "After Suthorn Ruins": "suthorn_ruins",
            "Unlocked after Suthorn Ruins; Lv. 3 after Still Missing": "suthorn_ruins",
            "Unlocked after Suthorn Ruins; Lv. 3-4 after Still Missing": "suthorn_ruins",
            "A Rift in the Gerudo Desert": "lueburrys_house",
            "After learning the Holmill Echo": "lueburrys_house",
            "After learning the Holmill Echo (Ancestor's Cave of Rest)": "lueburrys_house",
            "After learning Wind Cannon, Gustmaster or Tornando": "lueburrys_house",
            "After learning the Wind Cannon Echo": "lueburrys_house",
            "After Gerudo Sanctum": "gerudo_sanctum",
            "Bow obtained in the Gerudo Sanctum; Lv. 3 after Still Missing": "cryptic_cavern",
            "The Jabul Waters Rift": "lueburrys_house",
            "After Chaos at River Zora Village": "chaos_river_zora_village",
            "After learning the Bombfish Echo": "jabul_waters_rift_start",
            "After The Jabul Waters Rift": "jabul_ruins",
            "After Jabul Ruins": "jabul_ruins",
            "After Jabul Ruins (per Zelda Dungeon)": "jabul_ruins",
            "After Jabul Ruins and Gerudo Sanctum": ("gerudo_sanctum", "jabul_ruins"),
            # Still Missing runs from Lueburry's report to the King's rescue in Hyrule Castle.
            "Still Missing": ("gerudo_sanctum", "jabul_ruins"),
            "After Still Missing": "hyrule_castle",
            "After Still Missing (Hyrule Castle restored)": "hyrule_castle",
            "After Still Missing (per Zelda Dungeon)": "hyrule_castle",
            "Zelda Dungeon lists 'Complete Still Missing'": "hyrule_castle",
            "After Still Missing and two Lanmolas defeated": "hyrule_castle",
            "After Hyrule Castle": "hyrule_castle",
            "After Impa's Gift (needs your horse)": "hyrule_castle",
            "After Impa's Gift (Zelda's horse)": "hyrule_castle",
            "Impa's Gift side quest": "hyrule_castle",
            "The Rift on Eldin Volcano": "hyrule_castle",
            "After The Rift on Eldin Volcano": "eldin_temple",
            "After The Rift on Eldin Volcano, all Accessory slots upgraded and Might Crystals explained by Lueburry": "eldin_temple",
            "After Eldin Temple": "eldin_temple",
            "After Eldin Temple (per Zelda Dungeon)": "eldin_temple",
            "Bombs obtained in the Eldin Temple; Lv. 3 after Still Missing": "eldin_crater_shortcut",
            "Rift on Holy Mount Lanayru": "hyrule_castle",
            "During/after Rift on Holy Mount Lanayru": "hyrule_castle",
            "After Rift on Holy Mount Lanayru": "lanayru_temple",
            "After Lanayru Temple": "lanayru_temple",
            "After Lanayru Temple (per Zelda Dungeon)": "lanayru_temple",
            "A Rift in the Faron Wetlands": "hyrule_castle",
            "During/after A Rift in the Faron Wetlands (Stilled Blossu's House)": "hyrule_castle",
            "After A Rift in the Faron Wetlands": "faron_temple",
            "After Faron Temple": "faron_temple",
            "After Faron Temple (per Zelda Dungeon)": "faron_temple",
            "After Faron Temple and the Eastern Temple (Smog)": "faron_temple",
            "After Faron Temple and Lanayru Temple": ("lanayru_temple", "faron_temple"),
            "After Eldin Temple and Faron Temple": ("eldin_temple", "faron_temple"),
            "After Eldin Temple and Lanayru Temple": ("eldin_temple", "lanayru_temple"),
            "After Eldin, Faron and Lanayru Temples": EOW_GODDESSES,
            "The Prime Energy and Null": EOW_GODDESSES,
            "All dungeons except Null's Body (per Zelda Dungeon)": EOW_GODDESSES,
            "After A Curious Child, with Performance Artist! started": "lueburrys_house",
            "After 'Stamp Stand Swallowed!'": "hyrule_castle",
            "The Fireworks Artist side quest": "suthorn_ruins",
        },
        # Smoothie Shops and the Stamp Guy are outside Suthorn.
        "sections": {
            "smoothies/smoothies": "suthorn_ruins",
            "smoothies/potions": "suthorn_ruins",
            "stamp_stands/rewards": "suthorn_ruins",
        },
        "items": {
            "accessories/survey_scope": "suthorn_ruins",
            "accessories/survey_binoculars": "suthorn_ruins",
            "side_quests/recipes_please": "suthorn_ruins",
            "collectibles/bottle_stamps": "suthorn_ruins",
            "collectibles/stamp_suit": "suthorn_ruins",
        },
    },
    "mm": {
        # The first cycle is spent as a Deku in Clock Town; human Link and the rest of Termina come
        # with the Ocarina. Each temple opens with its song (the `temple_songs` steps), and what's in
        # a temple, or needs its item, is gated by that song. The Road to Ikana needs Epona, whose
        # song is won with the Hero's Bow; upper Ikana Canyon needs the Ice Arrows.
        "region": {
            "Clock Town": "reclaim_ocarina",
            "Termina Field": "reclaim_ocarina",
            "Romani Ranch": "reclaim_ocarina",
            "Southern Swamp": "reclaim_ocarina",
            "Woodfall": "reclaim_ocarina",
            "Snowhead": "reclaim_ocarina",
            "Great Bay": "reclaim_ocarina",
            "Ikana": "woodfall_sonata",
            "The Moon": MM_TEMPLES,
        },
        "after": {
            "Powder Keg, to blow up the boulder on Milk Road": "reclaim_ocarina",
            "All-Night Mask": "woodfall_sonata",
            "Giant's Wallet (500 Rupees)": "woodfall_sonata",
            "Hero's Bow": "woodfall_sonata",
            "Couple's Mask": "great_bay_bossa_nova",
            "Kamaro's Mask": "reclaim_ocarina",
            "Goron Mask": "reclaim_ocarina",
            "Zora Mask": "reclaim_ocarina",
            "Light Arrows": "ikana_elegy",
            "Captain's Hat": "woodfall_sonata",
            "Ikana Canyon": "great_bay_bossa_nova",
            "Ikana Canyon (Sakon's Hideout)": "great_bay_bossa_nova",
            "Romani's Mask, Goron and Zora Masks": "woodfall_sonata",
            "Bremen Mask and the Powder Keg": "reclaim_ocarina",
            "Epona's Song": "woodfall_sonata",
            "Epona's Song (Day 1, same cycle)": "woodfall_sonata",
            "Epona's Song and the Powder Keg": "woodfall_sonata",
            "Powder Keg, and Epona and the Hero's Bow": "woodfall_sonata",
            "Keaton Mask": "reclaim_ocarina",
            "Postman's Hat": "reclaim_ocarina",
            "Bomb Bag and Hero's Bow": "woodfall_sonata",
            "Any one transformation song": "reclaim_ocarina",
            "Pictograph Box": "reclaim_ocarina",
            "Woodfall Temple": "woodfall_temple",
            "Woodfall Temple, Hero's Bow": "woodfall_temple",
            "Lens of Truth, Hookshot and the Scarecrow's Song": "reclaim_ocarina",
            "Snowhead Temple": "snowhead_temple",
            "Snowhead Temple, Powder Keg": "snowhead_temple",
            "Goron Lullaby, before Snowhead Temple (while it's winter)": "snowhead_lullaby",
            "Don Gero's Mask, Woodfall and Great Bay temples": ("woodfall_temple", "great_bay_temple"),
            "Bottles": "reclaim_ocarina",
            "Pictograph Box, Zora Mask, a free Bottle": "reclaim_ocarina",
            "Zora Mask, Hookshot": "reclaim_ocarina",
            "Hookshot, Zora Mask": "reclaim_ocarina",
            "Zora Mask, Hookshot, Bottles": "reclaim_ocarina",
            "Hookshot": "reclaim_ocarina",
            "Captain's Hat, Hookshot, Hero's Bow": "woodfall_sonata",
            "Hookshot, Magic Beans and the Scarecrow's Song": "reclaim_ocarina",
            "Great Bay Temple": "great_bay_temple",
            "Captain's Hat, Lens of Truth": "woodfall_sonata",
            "Lens of Truth": "reclaim_ocarina",
            "Lens of Truth and a Bottle of potion": "reclaim_ocarina",
            "Ice Arrows, to reach upper Ikana Canyon": "great_bay_bossa_nova",
            "Ice Arrows, Hookshot": "great_bay_bossa_nova",
            "Ice Arrows and the Song of Storms": "great_bay_bossa_nova",
            "Gibdo Mask": "great_bay_bossa_nova",
            "Garo's or Gibdo Mask to get into Ikana Canyon": "woodfall_sonata",
            "Mirror Shield, Hero's Bow and Powder Keg or Fire Arrows": "great_bay_temple",
            "Elegy of Emptiness": "ikana_elegy",
            "All four Remains, Night of the Final Day": MM_TEMPLES,
        },
        "needs": {
            r"Woodfall Temple": "woodfall_sonata",
            r"Snowhead Temple|path up to Snowhead": "snowhead_lullaby",
            r"Great Bay Temple": "great_bay_bossa_nova",
            r"Stone Tower": "ikana_elegy",
            r"Ikana Canyon|Spirit House|Secret Shrine|Ancient Castle|Beneath the Well|Music Box House": "great_bay_bossa_nova",
            r"Road to Ikana|Ikana Graveyard|Beneath the Graveyard": "woodfall_sonata",
            r"Hero's Bow|Romani's Mask|Garo's Mask|Epona": "woodfall_sonata",
            r"Fire Arrow": "snowhead_lullaby",
            r"Ice Arrow|Gibdo Mask": "great_bay_bossa_nova",
            r"Light Arrow": "ikana_elegy",
            r"Mirror Shield": "great_bay_temple",
            r"human( Link| form)": "reclaim_ocarina",
        },
        "tags": {
            "Bow": "woodfall_sonata",
            "Fire Arrows": "snowhead_lullaby",
            "Ice Arrows": "great_bay_bossa_nova",
            "Light Arrows": "ikana_elegy",
            "Inverted": "ikana_elegy",
            "Elegy of Emptiness": "ikana_elegy",
            "Mirror Shield": "great_bay_temple",
            **{tag: "reclaim_ocarina" for tag in (
                "Goron", "Zora", "Hookshot", "Bombs", "Bottle", "Lens of Truth", "Magic Bean", "Sonata of Awakening",
                "Great Fairy's Mask", "Bugs",
            )},
        },
        # A temple's Stray Fairies are inside it; the Spider Houses are in the swamp and by the sea.
        "sections": {
            "stray_fairies/woodfall": "woodfall_sonata",
            "stray_fairies/snowhead": "snowhead_lullaby",
            "stray_fairies/great_bay": "great_bay_bossa_nova",
            "stray_fairies/stone_tower": "ikana_elegy",
            "spider_houses/swamp_spider_house": "reclaim_ocarina",
            "spider_houses/oceanside_spider_house": "reclaim_ocarina",
        },
        "items": {
            # What a Deku can do in the first cycle.
            "heart_pieces/north_clock_town_tree": None,
            "heart_pieces/clock_tower_ledge": None,
            "heart_pieces/deku_playground": None,
            "heart_pieces/bank_5000": None,
            "stray_fairies/clock_town_stray_fairy": None,
            "stray_fairies/great_fairy_clock_town_magic": None,
            "bombers_notebook/bombers": None,
            "bombers_notebook/bomb_shop_old_lady": None,
            "items/deku_sticks": None,
            "items/deku_nuts": None,
            "items/wallet": None,
            "items/magic": None,
            "world/bank_adult_wallet": None,
            "world/bank_interest": None,
            "world/bank_heart_piece": None,
            "world/owl_clock_town": None,
            "world/map_clock_town": None,
            "masks/kafei_mask": None,
            "masks/blast_mask": None,
            "songs/inverted_song_of_time": "reclaim_ocarina",
            "songs/song_of_double_time": "reclaim_ocarina",
            # Rewards of a step.
            "masks/deku_mask": "reclaim_ocarina",
            "songs/song_of_time": "reclaim_ocarina",
            "songs/song_of_healing": "reclaim_ocarina",
            "songs/oath_to_order": "woodfall_temple",
            # Each temple's Great Fairy takes its 15 fairies; Ikana's sits in the canyon.
            "stray_fairies/great_fairy_woodfall": "woodfall_sonata",
            "stray_fairies/great_fairy_snowhead": "snowhead_lullaby",
            "stray_fairies/great_fairy_great_bay": "great_bay_bossa_nova",
            "stray_fairies/great_fairy_ikana": "ikana_elegy",
            "stray_fairies/great_fairy_clock_town_mask": "reclaim_ocarina",
            "items/magic_beans": "reclaim_ocarina",
            "items/pictograph_box": "reclaim_ocarina",
            "items/lens_of_truth": "reclaim_ocarina",
            "items/bombchus": "reclaim_ocarina",
            "items/sword": "reclaim_ocarina",
            "items/quiver": "woodfall_sonata",
            "items/bomb_bag": "reclaim_ocarina",
            "items/bottle_kotake": "reclaim_ocarina",
            "items/bottle_aroma": "reclaim_ocarina",
            "world/owl_milk_road": "reclaim_ocarina",
            "world/owl_southern_swamp": "reclaim_ocarina",
            "world/owl_woodfall": "reclaim_ocarina",
            "world/owl_mountain_village": "reclaim_ocarina",
            "world/owl_snowhead": "snowhead_lullaby",
            "world/owl_great_bay_coast": "reclaim_ocarina",
            "world/owl_zora_cape": "reclaim_ocarina",
            "world/map_woodfall": "reclaim_ocarina",
            "world/map_snowhead": "reclaim_ocarina",
            "world/map_romani_ranch": "reclaim_ocarina",
            "world/map_great_bay": "reclaim_ocarina",
            "world/map_stone_tower": "great_bay_bossa_nova",
            "bombers_notebook/anju": "reclaim_ocarina",
            "bombers_notebook/curiosity_shop_man": "reclaim_ocarina",
            "bombers_notebook/romani": "woodfall_sonata",
            "bombers_notebook/cremia": "woodfall_sonata",
            "bombers_notebook/madame_aroma": "reclaim_ocarina",
            "bombers_notebook/gorman": "woodfall_sonata",
            "bombers_notebook/postman": "reclaim_ocarina",
            "bombers_notebook/toilet_hand": "reclaim_ocarina",
            "bombers_notebook/kamaro": "reclaim_ocarina",
            "bombers_notebook/shiro": "woodfall_sonata",
            "bombers_notebook/guru_guru": "reclaim_ocarina",
            # Lower Ikana Canyon is reached without the Ice Arrows.
            "heart_pieces/ikana_canyon_scrub": "woodfall_sonata",
            "heart_pieces/pictograph_contest": "reclaim_ocarina",
            # The song itself is learned on the way to its step, so it's had from the Ocarina on.
            "songs/sonata_of_awakening": "reclaim_ocarina",
            "spider_houses/swamp_sh_reward": "reclaim_ocarina",
            "spider_houses/ocean_sh_reward": "reclaim_ocarina",
        },
    },
    "oot": {
        # Childhood is open from the start, and Link can go back to it after the Master Sword. A
        # place is gated by the step that opens it, a dungeon by the step before it; tags name what
        # an item needs (Adult, the Hookshot, Bombs…). The latest wins.
        "region": OOT_PLACES,
        "area": OOT_PLACES,
        "after": {"Waking Talon at Hyrule Castle": "meet_zelda"},
        "tags": {
            "Adult": "master_sword",
            "Hookshot": "master_sword",
            "Epona": "master_sword",
            "Scarecrow's Song": "master_sword",
            "Song of Storms": "master_sword",
            "Bolero of Fire": "master_sword",
            "Boomerang": "meet_zelda",
            "Bombs": "meet_zelda",
            "Bomb Bag": "meet_zelda",
            "Saria's Song": "meet_zelda",
            "Sun's Song": "meet_zelda",
            "Silver Scale": "meet_zelda",
            "Zelda's Lullaby": "meet_zelda",
            "Goron's Bracelet": "meet_zelda",
            "Din's Fire": "meet_zelda",
            "Song of Time": "ocarina_of_time",
            "Megaton Hammer": "light_medallion",
            "Blue Fire": "light_medallion",
            "Zora Tunic": "light_medallion",
            "Fairy Bow": "light_medallion",
            "Iron Boots": "light_medallion",
            "Longshot": "ice_cavern",
            "Lens of Truth": "bottom_of_the_well",
            "Requiem of Spirit": "gerudo_fortress",
            "Silver Gauntlets": "gerudo_fortress",
            "Gerudo's Card": "gerudo_fortress",
            "Golden Gauntlets": OOT_TEMPLES,
        },
        # Big Poes are shot from Epona with the Fairy Bow.
        "sections": {"big_poes/big_poes": "light_medallion"},
        "items": {
            "items/ocarina": None,
            "items/bombchus": "meet_zelda",
            "items/dins_fire": "meet_zelda",
            "items/fire_arrows": "water_temple",
            "items/ice_arrows": "gerudo_fortress",
            "items/light_arrows": OOT_TEMPLES,
            "items/zora_tunic": "light_medallion",
            "items/biggorons_sword": "light_medallion",
            "items/strength": "meet_zelda",
            "items/scale": "meet_zelda",
            "items/bomb_bag": "meet_zelda",
            "items/quiver": "light_medallion",
            "items/gerudos_card": "gerudo_fortress",
            "songs/zeldas_lullaby": "meet_zelda",
            "songs/sarias_song": "meet_zelda",
            "songs/suns_song": "meet_zelda",
            "songs/song_of_time": "ocarina_of_time",
            "songs/prelude_of_light": "forest_temple",
            "songs/nocturne_of_shadow": ("forest_temple", "fire_temple", "water_temple"),
            "trading/zeldas_letter": "meet_zelda",
            # The Happy Mask Shop opens once Zelda's Letter has been shown to the Kakariko guard.
            "trading/keaton_mask": "meet_zelda",
            "trading/skull_mask": "meet_zelda",
            "trading/spooky_mask": "meet_zelda",
            # The Running Man appears once Link has all three Spiritual Stones.
            "trading/bunny_hood": ("deku_tree", "dodongos_cavern", "jabu_jabu"),
            "trading/mask_of_truth": ("deku_tree", "dodongos_cavern", "jabu_jabu"),
            "trading/pocket_egg": "master_sword",
            "trading/pocket_cucco": "master_sword",
            "trading/cojiro": "master_sword",
            "trading/odd_mushroom": "master_sword",
            "trading/odd_potion": "master_sword",
            "trading/poachers_saw": "master_sword",
            "trading/broken_gorons_sword": "master_sword",
            "trading/prescription": "master_sword",
            # King Zora is thawed with Blue Fire from the Ice Cavern.
            "trading/eyeball_frog": "light_medallion",
            "trading/eye_drops": "light_medallion",
            "trading/claim_check": "light_medallion",
            "trading/biggorons_sword": "light_medallion",
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


def ids(gate):
    """A gate's step ids: one step, or a tuple of steps that must all be done."""
    return (gate,) if isinstance(gate, str) else tuple(gate)


def text_of(item):
    """What an item says it needs: its area and how-to, or its first tier's how-to when it has tiers."""
    how = item["tiers"][0].get("howTo") if item.get("tiers") else item.get("howTo")
    return " ".join(t for t in (item.get("area"), how) if t)


def matches(table, text):
    """The steps of every pattern in `table` (regex to step) that `text` contains, with the pattern."""
    return [(step, pattern) for pattern, step in table.items() if re.search(pattern, text)]


def propose(game, stem, section, item, depth):
    """
    (step or None, basis) for one item. An item in a dungeon's region (the table's `inside`) is
    gated by the step that opens the dungeon, unless its `after` needs something later. A game
    with `needs` (regex to step) also gates on every item or place its text mentions, the latest
    winning; a `photo` section instead takes the earliest of the places its text lists, but never
    earlier than that section's floor.
    """
    table = GAMES[game]
    key = f"{stem}/{item['id']}"
    if key in table["items"]:
        return table["items"][key], "override"
    if f"{stem}/{section}" in table.get("photo", {}):
        floor = table["photo"][f"{stem}/{section}"]
        places = [m for m in matches(table.get("places", {}), item.get("howTo") or "") if m[0] is not None]
        if not places:
            return floor, "photo floor"
        step, pattern = min(places, key=lambda m: depth(m[0]))
        return (step, f"photo: {pattern}") if depth(step) > depth(floor) else (floor, "photo floor")
    found = None, "none"
    for field in ("after", "area", "region"):
        text = item.get(field)
        if text is not None and text in table.get(field, {}):
            found = table[field][text], field
            break
    candidates = [found] if found[0] is not None else []
    inside = table.get("inside", {}).get(item.get("region"))
    if inside is not None:
        candidates.append((inside, "inside"))
    text = text_of(item)
    candidates += [(step, f"needs: {pattern}") for step, pattern in matches(table.get("needs", {}), text) if step is not None]
    candidates += [(table["tags"][t], f"tag: {t}") for t in item.get("tags", []) if table.get("tags", {}).get(t) is not None]
    if table.get("sections", {}).get(f"{stem}/{section}") is not None:
        candidates.append((table["sections"][f"{stem}/{section}"], "section"))
    where = " ".join(t for t in (text, item.get("region")) if t)
    candidates += [(step, f"place: {pattern}") for step, pattern in matches(table.get("places", {}), where) if step is not None]
    if not candidates:
        return found
    # The latest wins; on a tie, the first found (after, then inside, then the text).
    return max(candidates, key=lambda c: depth(c[0]))


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
    by_step = story_depth(story)

    def depth(gate):
        """How far into the story a gate comes: its latest step's depth."""
        return max(by_step[s] for s in ids(gate))

    sticky = GAMES[game].get("sticky", set())
    refs = GAMES[game].get("refs", {})
    # A first pass, so an item that names another (`refs`: "Treasure Chart 33") can take its gate.
    first = {}
    for path in checklists(game):
        for section in json.loads(path.read_text())["sections"]:
            for item in section.get("items", []):
                first[item["id"]] = propose(game, path.stem, section["id"], item, depth)[0]
    known = {f"{path.stem}/{item['id']}" for path in checklists(game)
             for section in json.loads(path.read_text())["sections"] for item in section.get("items", [])}
    for key in GAMES[game]["items"]:
        if key not in known:
            raise SystemExit(f"override for unknown item '{key}'")
    rows = []
    for path in checklists(game):
        catalog = json.loads(path.read_text())
        text = path.read_text()
        gates = {}
        for section in catalog["sections"]:
            # In a `sticky` section (floors of a cave, say) an item with nothing of its own is gated like the one before.
            last = None
            for item in section.get("items", []):
                step, basis = propose(game, path.stem, section["id"], item, depth)
                if f"{path.stem}/{section['id']}" in sticky:
                    if basis == "none" and last is not None:
                        step, basis = last, "sticky"
                    last = step
                if basis != "override":
                    for pattern, template in refs.items():
                        for m in re.finditer(pattern, text_of(item)):
                            other = first.get(template.format(*m.groups()))
                            if other is not None and (step is None or depth(other) > depth(step)):
                                step, basis = other, f"ref: {m.group(0)}"
                if step is not None and any(s not in steps for s in ids(step)):
                    raise SystemExit(f"{path.stem}/{item['id']}: '{step}' isn't a Main Quest step")
                gates[item["id"]] = [step, basis, item]
        # Nothing can be had before what it `requires`: take the gate of a requirement that comes later.
        changed = True
        while changed:
            changed = False
            for gate in gates.values():
                for needed in gate[2].get("requires", []):
                    other = gates.get(needed, [None])[0]
                    if other is not None and (gate[0] is None or depth(other) > depth(gate[0])):
                        gate[0], gate[1], changed = other, f"requires: {needed}", True
        for step, basis, item in gates.values():
            rows.append((path.stem, item["id"], item["name"], item.get("after") or item.get("area") or "", "+".join(ids(step)) if step else "", basis))
            if apply and step is not None and "unlockedBy" not in item:
                text = insert_link(text, item["id"], list(ids(step)))
        if apply:
            path.write_text(text)
    print("file\tid\tname\tafter/area\tunlockedBy\tbasis")
    for row in rows:
        print("\t".join(row))


if __name__ == "__main__":
    main(sys.argv[1:])
