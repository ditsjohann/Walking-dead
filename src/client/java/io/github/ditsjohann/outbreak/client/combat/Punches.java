package io.github.ditsjohann.outbreak.client.combat;

/** Bare-handed, the local player punches left, right, left: which fist the current swing is. */
public final class Punches {
	private static boolean left;
	private static long lastSwing = Long.MIN_VALUE;

	private Punches() {
	}

	public static boolean isLeft() {
		return left;
	}

	/**
	 * A new swing with an empty hand: the other fist this time. The same swing can be reported twice
	 * (ours, then the server's echo), so only count swings a few ticks apart.
	 */
	public static void next(long gameTime) {
		// (Or a new world, whose clock started again.)
		if (gameTime - lastSwing >= 3 || gameTime < lastSwing) {
			left = !left;
		}

		lastSwing = gameTime;
	}
}
