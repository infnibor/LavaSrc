package com.github.topi314.lavasrc.deezer;

public class DeezerArlTester {
	public static void main(String[] args) {
		String arlUrl = "https://luke.gg/arl";
		DeezerTokenTracker tracker = new DeezerTokenTracker(null, arlUrl);
		System.out.println("Set arl as URL: " + arlUrl);

		try {
			String arlValue = tracker.getArl();
			System.out.println("Fetched arl value: " + arlValue);
		} catch (Exception e) {
			System.out.println("Error fetching arl: " + e.getMessage());
		}

		System.out.println("Simulating 403 error (expired arl)...");
		tracker.invalidateArlCache();
		try {
			String arlValue = tracker.getArl();
			System.out.println("After refresh, fetched arl value: " + arlValue);
		} catch (Exception e) {
			System.out.println("Error fetching arl after 403: " + e.getMessage());
		}
	}
}
