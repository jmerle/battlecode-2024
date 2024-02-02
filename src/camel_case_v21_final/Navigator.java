package camel_case_v21_final;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public class Navigator extends Globals {
    private static MapLocation currentTarget;

    private static int minDistanceToTarget;
    private static int roundsSinceMovingCloserToTarget;

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

        MapLocation nextLocation = myLocation.add(myLocation.directionTo(target));
        if (rc.canSenseLocation(nextLocation) && rc.senseMapInfo(nextLocation).isDam()) {
            return;
        }

        int distanceToTarget = myLocation.distanceSquaredTo(target);
        if (distanceToTarget < minDistanceToTarget) {
            minDistanceToTarget = distanceToTarget;
            roundsSinceMovingCloserToTarget = 0;
        } else {
            roundsSinceMovingCloserToTarget++;
        }

        if (roundsSinceMovingCloserToTarget < 3) {
            Direction bellmanFordDirection = BellmanFordNavigator.getBestDirection(target);
            if (bellmanFordDirection != null) {
                MapLocation bellmanFordLocation = rc.adjacentLocation(bellmanFordDirection);
                if (rc.canFill(bellmanFordLocation)) {
                    rc.fill(bellmanFordLocation);
                }

                if (rc.canMove(bellmanFordDirection)) {
                    rc.move(bellmanFordDirection);
                }

                Logger.log("bf " + bellmanFordDirection);
                return;
            } else {
                Logger.log("bf null");
            }
        } else {
            Logger.log("bf n/a");
        }

        if (!rc.isMovementReady()) {
            return;
        }

        BugNavigator.moveTo(target);
    }

    public static void reset() {
        currentTarget = null;

        minDistanceToTarget = Integer.MAX_VALUE;
        roundsSinceMovingCloserToTarget = 0;

        BugNavigator.reset();
    }
}
