package camel_case;

import battlecode.common.Clock;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;

public class RobotPlayer extends Globals {
    public static void run(RobotController robotController) {
        Globals.init(robotController);

        while (true) {
            act();
            Clock.yield();
        }
    }

    private static void act() {
        int startRound = rc.getRoundNum();
        int startBytecodes = Clock.getBytecodeNum();

        try {
            Unit.act();
            Logger.flush();
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }

        int endRound = rc.getRoundNum();
        int endBytecodes = Clock.getBytecodeNum();

        int usedBytecodes = startRound == endRound
            ? endBytecodes - startBytecodes
            : (GameConstants.BYTECODE_LIMIT - startBytecodes) + Math.max(0, endRound - startRound - 1) * GameConstants.BYTECODE_LIMIT + endBytecodes;

        double bytecodePercentage = (double) usedBytecodes / (double) GameConstants.BYTECODE_LIMIT * 100.0;

        if (startRound != endRound) {
            System.out.println("Bytecode overflow: " + usedBytecodes + " (" + bytecodePercentage + "%)");
        } else if (bytecodePercentage > 95) {
            System.out.println("High bytecode usage: " + usedBytecodes + " (" + bytecodePercentage + "%)");
        }
    }
}
