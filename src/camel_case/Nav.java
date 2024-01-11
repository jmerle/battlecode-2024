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
        if (!rc.isMovementReady() || myLocation.equals(target)) {
            rc.setIndicatorLine(myLocation, target, 255, 255, 0);
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
                rc.move(bfsDirection);
                return;
            }
        }

        BugNav.moveTo(target);
        rc.setIndicatorLine(rc.getLocation(), target, 255, 0, 0);
    }

    public static void reset() {
        currentTarget = null;

        minDistanceToTarget = Integer.MAX_VALUE;
        turnsSinceMovingCloserToTarget = 0;
        visited = new FastSet();

        BugNav.reset();
    }
}
