package camel_case_v21_final;

import battlecode.common.Direction;
import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;

public class SharedArray extends Globals {
    private static int ROUND_INDEX = 0;
    private static int POI_COUNT_INDEX = 1;
    private static int POI_OFFSET = 2;

    private static MapLocation[] flagSpawns;

    public static void update() throws GameActionException {
        int currentRound = rc.getRoundNum();

        if (rc.readSharedArray(ROUND_INDEX) != currentRound) {
            rc.writeSharedArray(ROUND_INDEX, currentRound);
            rc.writeSharedArray(POI_COUNT_INDEX, 0);
        }

        if (flagSpawns == null) {
            setFlagSpawns();
        }

        if (!rc.isSpawned()) {
            return;
        }

        FlagInfo[] flags = rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED);
        for (int i = flags.length; --i >= 0; ) {
            FlagInfo flag = flags[i];
            if ((flag.getTeam() == myTeam && (flag.isPickedUp() || !isFlagSpawn(flag.getLocation())))
                || (flag.getTeam() == opponentTeam && !flag.isPickedUp())
                || (flag.getTeam() == opponentTeam && rc.senseNearbyRobots(flag.getLocation(), 8, myTeam).length < 3)) {
                addPOI(flag.getLocation());
            }
        }
    }

    public static void addPOI(MapLocation location) throws GameActionException {
        int count = rc.readSharedArray(POI_COUNT_INDEX);

        for (int i = count; --i >= 0; ) {
            if (getPOI(i).equals(location)) {
                return;
            }
        }

        writeLocation(POI_OFFSET + count, location);
        rc.writeSharedArray(POI_COUNT_INDEX, count + 1);
    }

    public static int getPOICount() throws GameActionException {
        return rc.readSharedArray(POI_COUNT_INDEX);
    }

    public static MapLocation getPOI(int index) throws GameActionException {
        return readLocation(POI_OFFSET + index);
    }

    private static void writeLocation(int index, MapLocation location) throws GameActionException {
        rc.writeSharedArray(index, (location.y * 60 + location.x) + 1);
    }

    private static MapLocation readLocation(int index) throws GameActionException {
        int value = rc.readSharedArray(index) - 1;
        return new MapLocation(value % 60, value / 60);
    }

    private static void setFlagSpawns() {
        FastSet set = new FastSet();

        flagSpawns = new MapLocation[3];
        int nextFlagSpawn = 0;

        MapLocation[] spawnLocations = rc.getAllySpawnLocations();
        for (int i = spawnLocations.length; --i >= 0; ) {
            MapLocation location = spawnLocations[i];

            boolean isTopLeft = true;
            for (int j = adjacentDirections.length; --j >= 0; ) {
                if (set.contains(location.add(adjacentDirections[j]))) {
                    isTopLeft = false;
                    break;
                }
            }

            if (isTopLeft) {
                flagSpawns[nextFlagSpawn] = location.add(Direction.SOUTHWEST);
                nextFlagSpawn++;
            }

            set.add(location);
        }
    }

    private static boolean isFlagSpawn(MapLocation location) {
        return flagSpawns[0].equals(location) || flagSpawns[1].equals(location) || flagSpawns[2].equals(location);
    }
}
