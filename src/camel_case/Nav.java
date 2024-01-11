package camel_case;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public class Nav extends Globals {
    private static MapLocation currentTarget;

    private static int minDistanceToTarget;
    private static int turnsSinceMovingCloserToTarget;
    private static FastSet visited;

    public static void moveTo(MapLocation target) throws GameActionException {
        MapLocation myLocation = rc.getLocation();
        rc.setIndicatorLine(rc.getLocation(), target, 255, 0, 0);

        if (myLocation.equals(target)) {
            return;
        }

        if (currentTarget == null || !currentTarget.equals(target)) {
            reset();
        }

        currentTarget = target;
        visited.add(myLocation);

        int distanceToTarget = myLocation.distanceSquaredTo(target);
        if (distanceToTarget < minDistanceToTarget) {
            minDistanceToTarget = distanceToTarget;
            turnsSinceMovingCloserToTarget = 0;
        } else {
            turnsSinceMovingCloserToTarget++;
        }

        if (turnsSinceMovingCloserToTarget < 3) {
            Direction bfsDirection = BFSNav.getBestDirection(target, visited);
            if (bfsDirection != null) {
                MapLocation bfsLocation = rc.adjacentLocation(bfsDirection);
                if (rc.canMove(bfsDirection)) {
                    rc.move(bfsDirection);
                } else if (rc.canFill(bfsLocation)) {
                    rc.fill(bfsLocation);
                }

                return;
            }
        }

        if (!rc.isMovementReady()) {
            return;
        }

        BugNav.moveTo(target);
    }

    public static void reset() {
        currentTarget = null;

        minDistanceToTarget = Integer.MAX_VALUE;
        turnsSinceMovingCloserToTarget = 0;
        visited = new FastSet();

        BugNav.reset();
    }
}
