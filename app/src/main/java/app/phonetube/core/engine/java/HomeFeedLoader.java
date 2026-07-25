package app.phonetube.core.engine.java;

import android.util.Log;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads the home feed by calling contentService.getHome() (synchronous),
 * then calling continueGroup() on every empty group to expand it into
 * actual video items — matching what SmartTube's BrowsePresenter does.
 *
 * The Observable path (getHomeObserve) drops empty groups in
 * emitGroupsPartial because continueEmptyGroup() returns null for
 * groups without nextPageKey/channelId. The synchronous getHome()
 * does the same. This loader fixes both by calling continueGroup()
 * on every group that arrives empty.
 */
public class HomeFeedLoader {
    private static final String TAG = "HomeFeedLoader";

    public static class Result {
        public final List<MediaGroup> groups;
        public final boolean success;
        public final String error;

        public Result(List<MediaGroup> groups, boolean success, String error) {
            this.groups = groups;
            this.success = success;
            this.error = error;
        }
    }

    /**
     * Load home feed groups and expand empty ones via continueGroup().
     * This replicates what SmartTube's BrowsePresenter does after
     * receiving groups from getHomeObserve().
     */
    public static Result loadHomeSync(ContentService contentService) {
        try {
            List<MediaGroup> rawGroups = contentService.getHome();
            if (rawGroups == null) {
                Log.d(TAG, "getHome returned null");
                return new Result(new ArrayList<>(), true, null);
            }

            Log.d(TAG, "getHome returned " + rawGroups.size() + " groups");

            List<MediaGroup> expanded = new ArrayList<>();

            for (MediaGroup group : rawGroups) {
                if (group == null) continue;

                if (!group.isEmpty()) {
                    expanded.add(group);
                    continue;
                }

                try {
                    MediaGroup continued = contentService.continueGroup(group);
                    if (continued != null && !continued.isEmpty()) {
                        Log.d(TAG, "continueGroup expanded '" + group.getTitle() + "' -> "
                                + (continued.getMediaItems() != null ? continued.getMediaItems().size() : 0) + " items");
                        expanded.add(continued);
                    } else {
                        Log.d(TAG, "continueGroup returned empty for '" + group.getTitle() + "'");
                    }
                } catch (Exception e) {
                    Log.d(TAG, "continueGroup failed for '" + group.getTitle() + "': " + e.getMessage());
                }
            }

            Log.d(TAG, "loadHomeSync complete, " + expanded.size() + " groups after expansion");
            return new Result(expanded, true, null);
        } catch (Exception e) {
            Log.e(TAG, "loadHomeSync failed", e);
            return new Result(new ArrayList<>(), false, e.getMessage());
        }
    }
}
