package camel_case;

import battlecode.common.Direction;
import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;

public class Unit extends Globals {
    private static MapLocation wanderTarget;

    public static void performTurn() throws GameActionException {
        if (!rc.isSpawned()) {
            if (spawn()) {
                Nav.reset();
            } else {
                return;
            }
        }

        if (rc.hasFlag()) {
            bringFlagHome();
            return;
        }

        pickUpFlag();

        if (rc.hasFlag()) {
            bringFlagHome();
            return;
        }

        attackOpponent();
        healFriendly();

        moveToFlag();
        pickUpFlag();
    }

    private static boolean spawn() throws GameActionException {
        MapLocation[] spawnLocations = rc.getAllySpawnLocations();
        for (int i = spawnLocations.length; --i >= 0; ) {
            MapLocation location = spawnLocations[i];
            if (rc.canSpawn(location)) {
                rc.spawn(location);
                return true;
            }
        }

        return false;
    }

    private static void pickUpFlag() throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        FlagInfo[] flags = rc.senseNearbyFlags(GameConstants.INTERACT_RADIUS_SQUARED, opponentTeam);
        for (int i = flags.length; --i >= 0; ) {
            MapLocation location = flags[i].getLocation();
            if (rc.canPickupFlag(location)) {
                rc.pickupFlag(location);
                return;
            }
        }
    }

    private static void attackOpponent() throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        RobotInfo attackTarget = getAttackTarget(GameConstants.ATTACK_RADIUS_SQUARED);
        if (attackTarget != null) {
            if (rc.canAttack(attackTarget.location)) {
                rc.attack(attackTarget.location);
            }

            moveToSafety();
            return;
        }

        if (!rc.isMovementReady()) {
            return;
        }

        RobotInfo visibleTarget = getAttackTarget(GameConstants.VISION_RADIUS_SQUARED);
        if (visibleTarget != null) {
            Nav.moveTo(visibleTarget.location);

            if (rc.canAttack(visibleTarget.location)) {
                rc.attack(visibleTarget.location);
            }
        }
    }

    private static RobotInfo getAttackTarget(int radius) throws GameActionException {
        RobotInfo bestTarget = null;
        int minHealth = Integer.MAX_VALUE;
        int maxPriority = Integer.MIN_VALUE;

        RobotInfo[] robots = rc.senseNearbyRobots(radius, opponentTeam);
        for (int i = robots.length; --i >= 0; ) {
            RobotInfo robot = robots[i];

            int priority = robot.attackLevel * 100 + robot.healLevel * 10 + robot.buildLevel;
            if (robot.hasFlag()) {
                priority = 1000;
            }

            if (bestTarget == null || priority > maxPriority || (priority == maxPriority && robot.health < minHealth)) {
                bestTarget = robot;
                minHealth = robot.health;
                maxPriority = priority;
            }
        }

        return bestTarget;
    }

    private static void moveToSafety() throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        MapLocation myLocation = rc.getLocation();
        RobotInfo[] opponentRobots = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, opponentTeam);

        Direction bestDirection = null;

        int maxDistance = 0;
        for (int i = opponentRobots.length; --i >= 0; ) {
            maxDistance += myLocation.distanceSquaredTo(opponentRobots[i].location);
        }

        for (int i = adjacentDirections.length; --i >= 0; ) {
            Direction direction = adjacentDirections[i];
            if (!rc.canMove(direction)) {
                continue;
            }

            MapLocation newLocation = rc.adjacentLocation(direction);

            int distance = 0;
            for (int j = opponentRobots.length; --j >= 0; ) {
                distance += newLocation.distanceSquaredTo(opponentRobots[j].location);
            }

            if (distance > maxDistance) {
                bestDirection = direction;
                maxDistance = distance;
            }
        }

        if (bestDirection != null) {
            rc.move(bestDirection);
        }
    }

    private static void healFriendly() throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        RobotInfo adjacentTarget = getHealTarget(GameConstants.INTERACT_RADIUS_SQUARED);
        if (adjacentTarget != null && rc.canHeal(adjacentTarget.location)) {
            rc.heal(adjacentTarget.location);
            return;
        }

        if (!rc.isMovementReady()) {
            return;
        }

        RobotInfo moveTarget = getHealTarget(8);
        if (moveTarget != null && !rc.getLocation().isAdjacentTo(moveTarget.location)) {
            Nav.moveTo(moveTarget.location);

            if (rc.canHeal(moveTarget.location)) {
                rc.heal(moveTarget.location);
            }
        }
    }

    private static RobotInfo getHealTarget(int radius) throws GameActionException {
        RobotInfo bestTarget = null;
        int minHealth = GameConstants.DEFAULT_HEALTH;

        RobotInfo[] robots = rc.senseNearbyRobots(radius, myTeam);
        for (int i = robots.length; --i >= 0; ) {
            RobotInfo robot = robots[i];
            if (robot.health < minHealth) {
                bestTarget = robot;
                minHealth = robot.health;
            }
        }

        return bestTarget;
    }

    private static void bringFlagHome() throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        MapLocation myLocation = rc.getLocation();

        MapLocation bestLocation = null;
        int minDistance = Integer.MAX_VALUE;

        MapLocation[] locations = rc.getAllySpawnLocations();
        for (int i = locations.length; --i >= 0; ) {
            MapLocation location = locations[i];
            int distance = myLocation.distanceSquaredTo(location);

            if (distance < minDistance) {
                bestLocation = location;
                minDistance = distance;
            }
        }

        if (bestLocation != null) {
            Nav.moveTo(bestLocation);
        }
    }

    private static void moveToFlag() throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        MapLocation myLocation = rc.getLocation();

        MapLocation bestLocation = null;
        int minDistance = Integer.MAX_VALUE;

        FlagInfo[] flags = rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, opponentTeam);
        for (int i = flags.length; --i >= 0; ) {
            FlagInfo flag = flags[i];
            if (flag.isPickedUp()) {
                continue;
            }

            MapLocation location = flag.getLocation();
            int distance = myLocation.distanceSquaredTo(location);

            if (distance < minDistance) {
                bestLocation = location;
                minDistance = distance;
            }
        }

        if (bestLocation != null) {
            Nav.moveTo(bestLocation);
            return;
        }

        MapLocation[] locations = rc.senseBroadcastFlagLocations();
        for (int i = locations.length; --i >= 0; ) {
            MapLocation location = locations[i];
            int distance = myLocation.distanceSquaredTo(location);

            if (distance < minDistance) {
                bestLocation = location;
                minDistance = distance;
            }
        }

        if (bestLocation != null) {
            Nav.moveTo(bestLocation);
            return;
        }

        wander();
    }

    private static void wander() throws GameActionException {
        if (wanderTarget == null || rc.canSenseLocation(wanderTarget)) {
            wanderTarget = new MapLocation(RandomUtils.nextInt(mapWidth), RandomUtils.nextInt(mapHeight));
        }

        Nav.moveTo(wanderTarget);
    }
}
