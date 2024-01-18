package camel_case;

import battlecode.common.Direction;
import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.GlobalUpgrade;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.TrapType;

public class Unit extends Globals {
    private static MapLocation wanderTarget;
    private static MapLocation spawnLocation;

    public static void act() throws GameActionException {
        if (rc.canBuyGlobal(GlobalUpgrade.ATTACK)) {
            rc.buyGlobal(GlobalUpgrade.ATTACK);
        }

        if (rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
            rc.buyGlobal(GlobalUpgrade.HEALING);
        }

        if (rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) {
            rc.buyGlobal(GlobalUpgrade.CAPTURING);
        }

        if (!rc.isSpawned()) {
            if (spawn()) {
                spawnLocation = rc.getLocation();
                Navigator.reset();
            } else {
                return;
            }
        }

        SharedArray.update();

        if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS * 0.75) {
            moveToCrumbs();
            wander();
            return;
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

        if (rc.getRoundNum() >= GameConstants.SETUP_ROUNDS) {
            buildTrap();
            attackOpponent();
            attackOpponent();
            healFriendly();
            buildTrap();
            buildTrap();
            buildTrap();
        }

        if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS * 1.5) {
            moveToCrumbs();
        }

        moveToPOI();

        moveToFlag();
        pickUpFlag();
    }

    private static boolean spawn() throws GameActionException {
        MapLocation[] targets = new MapLocation[SharedArray.getPOICount()];
        for (int i = targets.length; --i >= 0; ) {
            targets[i] = SharedArray.getPOI(i);
        }

        MapLocation bestLocation = null;
        int minDistance = Integer.MAX_VALUE;

        MapLocation[] spawnLocations = rc.getAllySpawnLocations();

        if (targets.length == 0) {
            MapLocation preferredLocation = spawnLocations[myId % spawnLocations.length];
            if (rc.canSpawn(preferredLocation)) {
                Logger.log("spawn preferred " + preferredLocation);
                rc.spawn(preferredLocation);
                return true;
            }

            for (int i = spawnLocations.length - 1; --i >= 0; ) {
                MapLocation location = spawnLocations[i];
                if (rc.canSpawn(location)) {
                    Logger.log("spawn first " + bestLocation);
                    rc.spawn(location);
                    return true;
                }
            }

            return false;
        }

        for (int i = spawnLocations.length; --i >= 0; ) {
            MapLocation location = spawnLocations[i];
            if (!rc.canSpawn(location)) {
                continue;
            }

            int distance = Integer.MAX_VALUE - 1;
            for (int j = targets.length; --j >= 0; ) {
                distance = Math.min(distance, location.distanceSquaredTo(targets[j]));
            }

            if (distance < minDistance) {
                bestLocation = location;
                minDistance = distance;
            }
        }

        if (bestLocation != null) {
            Logger.log("spawn closest " + bestLocation);
            rc.spawn(bestLocation);
            return true;
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
                Logger.log("attack " + attackTarget.getID());
                rc.attack(attackTarget.location);
            }

            moveToSafety();
            return;
        }

        if (!rc.isMovementReady()) {
            return;
        }

        RobotInfo moveTarget = getAttackTarget(GameConstants.VISION_RADIUS_SQUARED);
        if (moveTarget != null) {
            Logger.log("attack move " + moveTarget.location);
            Navigator.moveTo(moveTarget.location);

            if (rc.canAttack(moveTarget.location)) {
                Logger.log("attack " + moveTarget.getID());
                rc.attack(moveTarget.location);
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

            int priority = robot.attackLevel + robot.healLevel + robot.buildLevel;
            if (robot.hasFlag) {
                priority += 1000;
            }

            if (bestTarget == null || priority > maxPriority || (priority == maxPriority && robot.health < minHealth)) {
                bestTarget = robot;
                minHealth = robot.health;
                maxPriority = priority;
            }
        }

        return bestTarget;
    }

    private static void buildTrap() throws GameActionException {
        if (!rc.isActionReady() || myId % 2 == 0) {
            return;
        }

        MapLocation bestLocation = null;
        int maxOpponents = 0;

        TrapType trapType = TrapType.STUN;

        for (int i = adjacentDirections.length; --i >= 0; ) {
            MapLocation trapLocation = rc.adjacentLocation(adjacentDirections[i]);
            if (!rc.canBuild(trapType, trapLocation)) {
                continue;
            }

            boolean hasNearbyTrap = false;
            for (int j = adjacentDirections.length; --j >= 0; ) {
                MapLocation otherLocation = trapLocation.add(adjacentDirections[j]);
                if (rc.canSenseLocation(otherLocation) && rc.senseMapInfo(otherLocation).getTrapType() != TrapType.NONE) {
                    hasNearbyTrap = true;
                    break;
                }
            }

            if (hasNearbyTrap) {
                continue;
            }

            int nearbyOpponents = rc.senseNearbyRobots(trapLocation, 5, opponentTeam).length;
            if (nearbyOpponents > maxOpponents) {
                bestLocation = trapLocation;
                maxOpponents = nearbyOpponents;
            }
        }

        if (bestLocation != null) {
            Logger.log("trap " + trapType + " " + bestLocation);
            rc.build(trapType, bestLocation);
        }
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
            Logger.log("safety " + bestDirection);
            rc.move(bestDirection);
        }
    }

    private static void healFriendly() throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        RobotInfo adjacentTarget = getHealTarget(GameConstants.INTERACT_RADIUS_SQUARED);
        if (adjacentTarget != null && rc.canHeal(adjacentTarget.location)) {
            Logger.log("heal " + adjacentTarget.getID());
            rc.heal(adjacentTarget.location);
            return;
        }

        if (!rc.isMovementReady()) {
            return;
        }

        RobotInfo moveTarget = getHealTarget(8);
        if (moveTarget != null && !rc.getLocation().isAdjacentTo(moveTarget.location)) {
            Logger.log("heal move " + moveTarget.location);
            Navigator.moveTo(moveTarget.location);

            if (rc.canHeal(moveTarget.location)) {
                Logger.log("heal " + moveTarget.getID());
                rc.heal(moveTarget.location);
            }
        }
    }

    private static RobotInfo getHealTarget(int radius) throws GameActionException {
        RobotInfo bestTarget = null;
        int minHealth = Integer.MAX_VALUE;
        int maxPriority = Integer.MIN_VALUE;

        RobotInfo[] robots = rc.senseNearbyRobots(radius, myTeam);
        for (int i = robots.length; --i >= 0; ) {
            RobotInfo robot = robots[i];
            if (robot.health == GameConstants.DEFAULT_HEALTH) {
                continue;
            }

            int priority = robot.attackLevel + robot.healLevel + robot.buildLevel;
            if (robot.hasFlag) {
                priority += 1000;
            }

            if (bestTarget == null || priority > maxPriority || (priority == maxPriority && robot.health < minHealth)) {
                bestTarget = robot;
                minHealth = robot.health;
                maxPriority = priority;
            }
        }

        return bestTarget;
    }

    private static void bringFlagHome() throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        if (rc.isMovementReady()) {
            Navigator.moveTo(spawnLocation);
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
            Logger.log("home " + bestLocation);
            Navigator.moveTo(bestLocation);
        }
    }

    private static void moveToCrumbs() throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        MapLocation myLocation = rc.getLocation();

        MapLocation bestLocation = null;
        int minDistance = Integer.MAX_VALUE;

        MapLocation[] locations = rc.senseNearbyCrumbs(GameConstants.VISION_RADIUS_SQUARED);
        for (int i = locations.length; --i >= 0; ) {
            MapLocation location = locations[i];

            int distance = myLocation.distanceSquaredTo(location);
            if (distance < minDistance) {
                bestLocation = location;
                minDistance = distance;
            }
        }

        if (bestLocation != null) {
            Logger.log("crumbs " + bestLocation);
            Navigator.moveTo(bestLocation);
        }
    }

    private static void moveToPOI() throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        MapLocation myLocation = rc.getLocation();

        MapLocation bestLocation = null;
        int minDistance = Integer.MAX_VALUE;

        for (int i = SharedArray.getPOICount(); --i >= 0; ) {
            MapLocation location = SharedArray.getPOI(i);
            int distance = myLocation.distanceSquaredTo(location);

            if (distance < minDistance) {
                bestLocation = location;
                minDistance = distance;
            }
        }

        if (bestLocation != null) {
            Logger.log("poi " + bestLocation);
            Navigator.moveTo(bestLocation);
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
            Logger.log("flag " + bestLocation);
            Navigator.moveTo(bestLocation);
            return;
        }

        MapLocation[] broadcasts = rc.senseBroadcastFlagLocations();
        if (broadcasts.length > 0) {
            MapLocation myBroadcast = broadcasts[myId % broadcasts.length];
            Logger.log("broadcast " + myBroadcast);
            Navigator.moveTo(myBroadcast);
            return;
        }

        wander();
    }

    private static void wander() throws GameActionException {
        int halfSize = Math.max(mapWidth, mapHeight) / 2;
        int maxDistance = halfSize * halfSize;

        while (wanderTarget == null
            || rc.canSenseLocation(wanderTarget)
            || (rc.getRoundNum() < GameConstants.SETUP_ROUNDS && spawnLocation.distanceSquaredTo(wanderTarget) > maxDistance)) {
            wanderTarget = new MapLocation(RandomUtils.nextInt(mapWidth), RandomUtils.nextInt(mapHeight));
        }

        Logger.log("wander " + wanderTarget);
        Navigator.moveTo(wanderTarget);
    }
}
