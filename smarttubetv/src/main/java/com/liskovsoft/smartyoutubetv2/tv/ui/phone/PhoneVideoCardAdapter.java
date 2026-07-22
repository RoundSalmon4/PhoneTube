package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared adapter for displaying video cards in horizontal rows and vertical grids.
 * Pulled out of the 5 fragments that were all copy-pasting the same ViewHolder.
 */
class PhoneVideoCardAdapter extends RecyclerView.Adapter<PhoneVideoCardAdapter.VideoViewHolder> {

    interface OnVideoClickListener {
        void onClick(Video video);
        void onLongClick(Video video);
    }

    private List<Video> mVideos = new ArrayList<>();
    private OnVideoClickListener mListener;

    PhoneVideoCardAdapter() {}

    PhoneVideoCardAdapter(OnVideoClickListener listener) {
        mListener = listener;
    }

    void setListener(OnVideoClickListener listener) {
        mListener = listener;
    }

    void setVideos(List<Video> videos) {
        mVideos = videos != null ? videos : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_phone_video_card, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video video = mVideos.get(position);

        holder.title.setText(video.getTitle());
        holder.channelName.setText(video.getAuthor());
        holder.viewsDate.setText(video.getSecondTitle());

        String thumbnailUrl = ClickbaitRemover.updateThumbnail(video, 0);
        if (thumbnailUrl == null) {
            thumbnailUrl = video.getCardImageUrl();
        }

        Glide.with(holder.itemView)
                .load(thumbnailUrl)
                .centerCrop()
                .into(holder.thumbnail);

        if (mListener != null) {
            holder.itemView.setOnClickListener(v -> mListener.onClick(video));
            holder.itemView.setOnLongClickListener(v -> {
                mListener.onLongClick(video);
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return mVideos != null ? mVideos.size() : 0;
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title;
        TextView channelName;
        TextView viewsDate;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            title = itemView.findViewById(R.id.video_title);
            channelName = itemView.findViewById(R.id.video_channel_name);
            viewsDate = itemView.findViewById(R.id.video_views_date);
        }
    }
}
