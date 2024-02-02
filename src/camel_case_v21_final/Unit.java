package camel_case_v21_final;

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

            if (rc.hasFlag()) {
                return;
            }
        }

        pickUpFlag();

        if (rc.hasFlag()) {
            bringFlagHome();

            if (rc.hasFlag()) {
                return;
            }
        }

        if (rc.getRoundNum() >= GameConstants.SETUP_ROUNDS) {
            buildTrap();
            attackOpponent();
            attackOpponent();
            healFriendly();
            buildTrap();
            buildTrap();
            buildTrap();
            moveToSafety();
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
                Logger.log("pickup " + location);
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
        if (attackTarget != null && rc.canAttack(attackTarget.location)) {
            Logger.log("attack 1 " + attackTarget.getID());
            rc.attack(attackTarget.location);
            return;
        }

        if (!rc.isMovementReady()) {
            return;
        }

        Direction bestMoveDirection = null;
        RobotInfo bestMoveTarget = null;
        int maxScore = Integer.MIN_VALUE;

        for (int i = adjacentDirections.length; --i >= 0; ) {
            Direction direction = adjacentDirections[i];
            if (!rc.canMove(direction)) {
                continue;
            }

            MapLocation newLocation = rc.adjacentLocation(direction);
            RobotInfo newTarget = getAttackTarget(newLocation, GameConstants.ATTACK_RADIUS_SQUARED);
            if (newTarget == null) {
                continue;
            }

            int score = rc.senseMapInfo(newLocation).getCrumbs();

            RobotInfo[] nearbyOpponents = rc.senseNearbyRobots(newLocation, 10, opponentTeam);
            for (int j = nearbyOpponents.length; --j >= 0; ) {
                if (!nearbyOpponents[j].hasFlag) {
                    score--;
                }
            }

            if (score > maxScore) {
                bestMoveDirection = direction;
                bestMoveTarget = newTarget;
                maxScore = score;
            }
        }

        if (bestMoveDirection != null) {
            Logger.log("attack 2 move " + bestMoveDirection);
            rc.move(bestMoveDirection);

            if (rc.canAttack(bestMoveTarget.location)) {
                Logger.log("attack 2 " + bestMoveTarget.getID());
                rc.attack(bestMoveTarget.location);
            }

            return;
        }

        RobotInfo moveTarget = getAttackTarget(GameConstants.VISION_RADIUS_SQUARED);
        if (moveTarget != null) {
            Logger.log("attack 3 move " + moveTarget.location);
            Navigator.moveTo(moveTarget.location);
        }
    }

    private static RobotInfo getAttackTarget(int radius) throws GameActionException {
        return getAttackTarget(rc.getLocation(), radius);
    }

    private static RobotInfo getAttackTarget(MapLocation center, int radius) throws GameActionException {
        RobotInfo bestTarget = null;
        int minHealth = Integer.MAX_VALUE;
        int maxPriority = Integer.MIN_VALUE;

        RobotInfo[] robots = rc.senseNearbyRobots(center, radius, opponentTeam);
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
        if (!rc.isActionReady()) {
            return;
        }

        TrapType trapType = TrapType.STUN;

        MapLocation bestLocation = null;
        int maxOpponents = rc.getCrumbs() > GameConstants.ROBOT_CAPACITY * trapType.buildCost ? 2 : 3;

        for (int i = adjacentDirections.length; --i >= 0; ) {
            MapLocation trapLocation = rc.adjacentLocation(adjacentDirections[i]);
            if (!rc.canBuild(trapType, trapLocation)) {
                continue;
            }

            int nearbyOpponents = rc.senseNearbyRobots(trapLocation, 8, opponentTeam).length;
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

        int minDangerousOpponents = 0;
        for (int i = opponentRobots.length; --i >= 0; ) {
            RobotInfo robot = opponentRobots[i];
            if (!robot.hasFlag && myLocation.distanceSquaredTo(robot.location) <= 10) {
                minDangerousOpponents++;
            }
        }

        for (int i = adjacentDirections.length; --i >= 0; ) {
            Direction direction = adjacentDirections[i];
            if (!rc.canMove(direction)) {
                continue;
            }

            MapLocation newLocation = rc.adjacentLocation(direction);

            int dangerousOpponents = 0;
            for (int j = opponentRobots.length; --j >= 0; ) {
                RobotInfo robot = opponentRobots[j];
                if (!robot.hasFlag && newLocation.distanceSquaredTo(robot.location) <= 10) {
                    dangerousOpponents++;
                }
            }

            if (dangerousOpponents < minDangerousOpponents) {
                bestDirection = direction;
                minDangerousOpponents = dangerousOpponents;
            }
        }

        if (bestDirection != null) {
            Logger.log("safety " + bestDirection);
            rc.move(bestDirection);
        }
    }

    private static void healFriendly() throws GameActionException {
        if (!rc.isActionReady() || rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, opponentTeam).length > 0) {
            return;
        }

        Direction bestDirection = null;
        RobotInfo bestTarget = null;
        int maxScore = Integer.MIN_VALUE;

        for (int i = allDirections.length; --i >= 0; ) {
            Direction direction = allDirections[i];
            if (direction != Direction.CENTER && !rc.canMove(direction)) {
                continue;
            }

            MapLocation newLocation = rc.adjacentLocation(direction);
            RobotInfo newTarget = getHealTarget(newLocation, GameConstants.HEAL_RADIUS_SQUARED);
            if (newTarget == null) {
                continue;
            }

            int score = newTarget.attackLevel + newTarget.healLevel + newTarget.buildLevel;
            if (newTarget.hasFlag) {
                score += 1000;
            }

            if (score > maxScore) {
                bestDirection = direction;
                bestTarget = newTarget;
                maxScore = score;
            }
        }

        if (bestDirection != null) {
            if (bestDirection != Direction.CENTER) {
                Logger.log("heal move " + bestDirection);
                rc.move(bestDirection);
            }

            if (rc.canHeal(bestTarget.location)) {
                Logger.log("heal " + bestTarget.getID());
                rc.heal(bestTarget.location);
            }
        }
    }

    private static RobotInfo getHealTarget(MapLocation center, int radius) throws GameActionException {
        RobotInfo bestTarget = null;
        int minHealth = Integer.MAX_VALUE;
        int maxPriority = Integer.MIN_VALUE;

        RobotInfo[] robots = rc.senseNearbyRobots(center, radius, myTeam);
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

        Logger.log("home " + spawnLocation);
        Navigator.moveTo(spawnLocation);
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
        if (!rc.isMovementReady()) {
            return;
        }

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
