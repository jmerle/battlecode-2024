from pathlib import Path
from queue import Queue

def distance(dx: int, dy: int) -> int:
    return dx ** 2 + dy ** 2

def direction(dx: int, dy: int) -> str:
    horizontal = {
        -1: "WEST",
        0: "",
        1: "EAST"
    }[dx]

    vertical = {
        -1: "SOUTH",
        0: "",
        1: "NORTH"
    }[dy]

    return f"Direction.{vertical}{horizontal}"

def main() -> None:
    directions = [(-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (-1, 1), (1, -1), (1, 1)]
    offsets = {}

    queue = Queue()
    for dx, dy in directions:
        queue.put((dx, dy, 0, direction(dx, dy)))

    while not queue.empty():
        (dx, dy, previous_id, direction_from_previous) = queue.get()

        if (dx, dy) in offsets:
            continue

        id = len(offsets) + 1
        neighbors_before = []
        outer_ring = False

        for direction_dx, direction_dy in directions:
            new_dx, new_dy = dx + direction_dx, dy + direction_dy
            new_distance = distance(new_dx, new_dy)

            if new_distance < distance(dx, dy):
                if new_dx != 0 or new_dy != 0:
                    neighbors_before.append(offsets[(new_dx, new_dy)]["id"])
            elif new_distance <= 20:
                queue.put((new_dx, new_dy, id, direction(direction_dx, direction_dy)))
            else:
                outer_ring = True

        offsets[(dx, dy)] = {
            "id": id,
            "previous_id": previous_id,
            "direction_from_previous": direction_from_previous,
            "outer_ring": outer_ring,
            "neighbors_before": neighbors_before,
            "neighbors_after": [],
        }

    for dx, dy in offsets:
        neighbors_after = offsets[(dx, dy)]["neighbors_after"]

        for direction_dx, direction_dy in directions:
            new_dx, new_dy = dx + direction_dx, dy + direction_dy
            new_distance = distance(new_dx, new_dy)

            if new_distance >= distance(dx, dy) and new_distance <= 20:
                neighbors_after.append(offsets[(new_dx, new_dy)]["id"])

    offsets_by_distance = list(sorted(offsets.keys(), key=lambda item: distance(item[0], item[1])))

    content = f"""
package camel_case;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;

// Generated by scripts/generate_bellman_ford.py
public class BellmanFordNavigator extends Globals {{
    public static Direction getBestDirection(MapLocation target) throws GameActionException {{
        boolean hasFlag = rc.hasFlag();
    """.strip() + "\n"

    for dx, dy in offsets_by_distance:
        data = offsets[(dx, dy)]
        id = data["id"]

        if distance(dx, dy) > 2:
            continue

        content += f"""
        MapLocation location{id} = rc.adjacentLocation({data['direction_from_previous']});
        boolean canVisit{id} = rc.canMove({direction(dx, dy)}) || rc.canFill(location{id});
        """.rstrip() + "\n"

    inner_ring_ids = [offsets[(dx, dy)]["id"] for dx, dy in offsets_by_distance if distance(dx, dy) <= 2]
    for id1 in inner_ring_ids:
        dx, dy = next((dx, dy) for dx, dy in offsets_by_distance if offsets[(dx, dy)]["id"] == id1)
        other_ids = [id2 for id2 in inner_ring_ids if id1 != id2]

        content += f"""
        if (canVisit{id1} && {' && '.join(f'!canVisit{id2}' for id2 in other_ids)}) {{
            return {direction(dx, dy)};
        }}
        """.rstrip() + "\n"

    content += f"""
        if ({' && '.join(f'!canVisit{id}' for id in inner_ring_ids)}) {{
            return null;
        }}

        boolean checkOpponents = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, opponentTeam).length > 0;
    """.rstrip() + "\n"

    for dx, dy in offsets_by_distance:
        data = offsets[(dx, dy)]
        id = data["id"]

        if distance(dx, dy) > 2:
            content += f"""
        MapLocation location{id} = location{data['previous_id']}.add({data['direction_from_previous']});
        boolean canVisit{id} = rc.canSenseLocation(location{id}) && (rc.sensePassability(location{id}) || (!hasFlag && rc.senseMapInfo(location{id}).isWater()));
            """.rstrip()

        content += f"""
        int distance{id} = 1_000_000;
        Direction direction{id} = null;
        int weight{id} = canVisit{id} ? (rc.senseMapInfo(location{id}).isWater() ? {2 + max(abs(dx), abs(dy))} : 1) + (checkOpponents ? rc.senseNearbyRobots(location{id}, 10, opponentTeam).length : 0) : 1_000_000;
        """.rstrip() + "\n"

    for i, (side, ordered_offsets) in enumerate([
        ("before", offsets_by_distance),
        ("after", reversed(offsets_by_distance)),
        ("before", offsets_by_distance)
    ]):
        for dx, dy in ordered_offsets:
            data = offsets[(dx, dy)]
            id = data["id"]
            neighbors = data[f"neighbors_{side}"]

            if i == 0 and distance(dx, dy) <= 2:
                content += f"""
        if (canVisit{id}) {{
            distance{id} = weight{id};
            direction{id} = {direction(dx, dy)};
        }}
                """.rstrip() + "\n"
                continue

            if len(neighbors) == 0:
                continue

            content += f"""
        if (canVisit{id}) {{
            """.rstrip()

            for neighbor in neighbors:
                content += f"""
            if (distance{neighbor} + weight{id} < distance{id}) {{
                distance{id} = distance{neighbor} + weight{id};
                direction{id} = direction{neighbor};
            }}
                """.rstrip()

                if neighbor != neighbors[-1]:
                    content += "\n"

            content += """
        }
            """.rstrip() + "\n"

    content += f"""
        Direction bestDirection = null;
        double maxScore = 0;
        int currentDistance = rc.getLocation().distanceSquaredTo(target);
    """.rstrip() + "\n"

    outer_ring_ids = [data["id"] for data in offsets.values() if data["outer_ring"]]
    for id in outer_ring_ids:
        content += f"""
        double score{id} = (double) (currentDistance - location{id}.distanceSquaredTo(target)) / (double) distance{id};
        if (score{id} > maxScore) {{
            bestDirection = direction{id};
            maxScore = score{id};
        }}
        """.rstrip() + "\n"

    content += """
        return bestDirection;
    }
}
    """.rstrip()

    output_file = Path(__file__).parent.parent / "src" / "camel_case" / f"BellmanFordNavigator.java"
    with output_file.open("w+", encoding="utf-8") as file:
        file.write(content.strip() + "\n")

if __name__ == "__main__":
    main()
