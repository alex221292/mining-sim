# Material rules (M2.4)

IDs are explicit: AIR=0 (empty), STONE=1 (static), SAND=2 (granular), WATER=3 (liquid), WOOD=4 (static), GRAVEL=5 (granular), SOIL=6 (granular). Granular materials displace water by exchanging places only when moving; water cannot displace solids, and granular materials cannot displace each other, stone or wood. A particle cannot move twice in a tick.

Sand falls vertically, then diagonally into air or water. Gravel follows the same priorities, but a diagonal is allowed only if the immediately adjacent cell at the original height is AIR; this prevents cutting through a crowded side and creates steeper piles in the test fixture. Gravel never moves horizontally. Neither material crosses the world boundary.

The deterministic pile fixture is three cells wide with a stone floor, two particles stacked at the center, and stone beside the upper particle on both sides. After one tick the center column has one sand cell but two gravel cells: the lower diagonal destination is open, while the upper lateral cell is blocked. This is a controlled local steepness comparison, not a measured universal angle of repose.

Soil uses deliberately conservative vertical cohesion: stone, wood, or soil immediately below holds it; otherwise soil tries to fall straight down into air or water. Soil never moves diagonally or sideways, and does not attach horizontally. A vertical soil stack without a solid floor falls from its bottom upward, so a disconnected cluster cannot remain suspended merely through mutual support. Sand or gravel below blocks soil's vertical move but is not cohesion; it will fall once that blocking particle moves away. The bottom boundary blocks motion. This is a local cellular rule, not structural analysis; overhangs are unsupported and cave roofs, loads and realistic soil mechanics are not modeled.

The water rules remain as described in `water-flow.md`. FULL_SCAN is the default; ACTIVE_REGIONS is experimental as described in `m2-activity.md`.
