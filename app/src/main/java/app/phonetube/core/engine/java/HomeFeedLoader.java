package app.phonetube.core.engine.java;

import android.util.Log;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * Java wrapper for home feed loading that replicates SmartTube's BrowsePresenter behavior.
 *
 * SmartTube's BrowsePresenter subscribes to homeObserve with .subscribe() and processes
 * each MediaGroup as it arrives. PhoneTube's Kotlin code uses .toList().await() which
 * waits for all batches before processing — this loses content because the Observable
 * completes with 0 batches for the "default" browseId on anonymous users.
 *
 * This class subscribes to the Observable exactly like SmartTube does and collects results.
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
     * Subscribe to homeObserve using SmartTube's .subscribe() pattern.
     * Blocks until the Observable completes and returns all collected MediaGroups.
     */
    public static Result loadHomeSync(Observable<List<MediaGroup>> observable) {
        List<MediaGroup> collected = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        final String[] error = {null};

        Disposable disposable = observable.subscribe(
            batch -> {
                Log.d(TAG, "received batch of " + batch.size() + " groups");
                collected.addAll(batch);
            },
            e -> {
                Log.e(TAG, "homeObserve error: " + e.getMessage(), e);
                error[0] = e.getMessage();
                latch.countDown();
            },
            () -> {
                Log.d(TAG, "homeObserve complete, " + collected.size() + " groups collected");
                latch.countDown();
            }
        );

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted waiting for homeObserve", e);
            error[0] = e.getMessage();
        }

        disposable.dispose();

        if (error[0] != null) {
            return new Result(new ArrayList<>(), false, error[0]);
        }

        return new Result(collected, true, null);
    }
}
