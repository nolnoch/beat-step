package com.nolnoch.beattothestep;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class SongSelectActivity extends Activity {

	private SimpleCursorAdapter songAdapter;
	private ListView songList;
	private Cursor songCursor, artCursor;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_songs);

		populateList();
	}

	@SuppressWarnings("deprecation")
	private void populateList() {
		songList = (ListView) findViewById(R.id.songs_list);

		String[] projection = {
				MediaStore.Audio.Media._ID,
				MediaStore.Audio.Media.TITLE,
				MediaStore.Audio.Media.ARTIST,
				MediaStore.Audio.Media.ALBUM_ID,
				MediaStore.Audio.Media.DATA
		};
		String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
		
		songCursor = getApplicationContext().getContentResolver().query(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				projection,
				selection,
				null,
				null
		);

		String[] from = new String[] {MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST};
		int[] to = new int[] {R.id.text1, R.id.text2};
		
		songAdapter = new SimpleCursorAdapter(
				getApplicationContext(),
				R.layout.list_item_song,
				songCursor,
				from,
				to
		);

		songList.setAdapter(songAdapter);
		songList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent data = new Intent();
				
				songCursor.moveToPosition(position);
				String path = songCursor.getString(
						songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
				data.setData(Uri.parse(path));
				data.putExtra("title", songCursor.getString(
						songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)));
				data.putExtra("artist", songCursor.getString(
						songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)));
				
				String albumID = songCursor.getString(
						songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
				
				String[] projection = {
						MediaStore.Audio.Albums._ID,
						MediaStore.Audio.Albums.ALBUM_ART 
				};
				String selection = MediaStore.Audio.Albums._ID + "=?";
				
				artCursor = getApplicationContext().getContentResolver().query(
						MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
						projection,
						selection,
						new String[] {albumID},
						null
				);
				
				artCursor.moveToFirst();
				data.putExtra("art", artCursor.getString(
						artCursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)));
				setResult(RESULT_OK, data);
				artCursor.close();
				
				songCursor.close();
				finish();
			}
			
		});
	}
	
	public void selectCancel(View v) {
		setResult(RESULT_CANCELED);
		songCursor.close();
		finish();
	}
	
	/*
	 * Necessary?
	 * 
	private class MyCursorAdapter extends SimpleCursorAdapter {

        @SuppressWarnings("deprecation")
		public MyCursorAdapter(Context context, int layoutId, Cursor cur, String[] from, int[] to) {
            super(context, layoutId, cur, from, to);
        }

        @Override
        public void bindView(View view, Context context, Cursor cur) {
            TextView tvTitle = (TextView) findViewById(R.id.text1);
            tvTitle.setText(cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.TITLE)));
            
            TextView tvArtist = (TextView) findViewById(R.id.text2);
            tvArtist.setText(cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.ARTIST)));
        }
    }
    */

}
