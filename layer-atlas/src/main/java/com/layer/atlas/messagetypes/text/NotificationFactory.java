package com.keeptruckin.android.view.messages.controls.Message;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.layer.atlas.messagetypes.AtlasCellFactory;
import com.layer.atlas.messagetypes.text.TextCellFactory;
import com.layer.atlas.provider.Participant;
import com.layer.atlas.provider.ParticipantProvider;
import com.layer.atlas.util.Util;
import com.layer.sdk.LayerClient;
import com.layer.sdk.messaging.Actor;
import com.layer.sdk.messaging.Message;

import com.keeptruckin.android.R;
import com.layer.sdk.messaging.MessagePart;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationCellFactory extends AtlasCellFactory<NotificationCellFactory.CellHolder, NotificationCellFactory.TextInfo> implements View.OnLongClickListener
{
	// PROPERTIES
	//================================================================================
	public static final String MIME_TYPE = "text/notification";

	// LIFE CYCLE
	//================================================================================
	public NotificationCellFactory()
	{
		super(256 * 1024);
	}

	public static boolean isType(Message message)
	{
		if (message != null && message.getMessageParts() != null && message.getMessageParts().size() > 0)
			return message.getMessageParts().get(0).getMimeType().equals(MIME_TYPE);
		return false;
	}

	public static String getMessagePreview(Context context, Message message)
	{
		return "";  //shouldn't show preview messages for notifications
	}


	// OVERRIDE ATLAS CELL FACTORY
	//================================================================================
	@Override
	public boolean isBindable(Message message)
	{
		return NotificationCellFactory.isType(message);
	}

	@Override
	public void bindCellHolder(CellHolder cellHolder, TextInfo parsed, Message message, CellHolderSpecs specs)
	{
		cellHolder.mTextView.setText(getBoldedText(parsed.getString()));
		cellHolder.mTextView.setTag(parsed);
		cellHolder.mTextView.setOnLongClickListener(this);
	}

	@Override
	public TextInfo parseContent(LayerClient layerClient, ParticipantProvider participantProvider, Message message) {
		MessagePart part = message.getMessageParts().get(0);
		String text = part.isContentReady() ? new String(part.getData()) : "";
		return new TextInfo(text, "");
	}

	@Override
	public CellHolder createCellHolder(ViewGroup cellView, boolean isMe, LayoutInflater layoutInflater)
	{
		View v = layoutInflater.inflate(R.layout.table_row_notification_message, cellView, true);
		LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)v.getLayoutParams();

		// margin on the other side is built into the container cell
		int margin = (int)layoutInflater.getContext().getResources().getDimension(com.layer.atlas.R.dimen.atlas_padding_normal);
		if (isMe)
		{
			params.setMarginEnd(margin);
			params.setMarginStart(0);
		}
		else
		{
			params.setMarginStart(margin);
			params.setMarginEnd(0);
		}


		TextView t = (TextView)v.findViewById(R.id.cell_text);
		t.setTextSize(0, this.mMessageStyle.getOtherTextSize());
		t.setLinkTextColor(this.mMessageStyle.getOtherTextColor());
		t.setTypeface(this.mMessageStyle.getOtherTextTypeface(), this.mMessageStyle.getOtherTextStyle());
		return new NotificationCellFactory.CellHolder(v);
	}

	// EVENTS
	//================================================================================
	@Override
	public boolean onLongClick(View v)
	{
		TextInfo parsed = (TextInfo) v.getTag();
		String text = parsed.getClipboardPrefix() + parsed.getString();
		Util.copyToClipboard(v.getContext(), R.string.atlas_text_cell_factory_clipboard_description, text);
		Toast.makeText(v.getContext(), R.string.atlas_text_cell_factory_copied_to_clipboard, Toast.LENGTH_SHORT).show();
		return true;
	}

	// CELL HOLDER
	//================================================================================
	public static class CellHolder extends AtlasCellFactory.CellHolder
	{
		TextView mTextView;

		public CellHolder(View view)
		{
			mTextView = (TextView) view.findViewById(R.id.cell_text);
		}
	}

	// TEXT INFO
	//================================================================================
	public static class TextInfo implements AtlasCellFactory.ParsedContent
	{
		private final String mString;
		private final String mClipboardPrefix;
		private final int mSize;

		public TextInfo(String string, String clipboardPrefix)
		{
			mString = string;
			mClipboardPrefix = clipboardPrefix;
			mSize = mString.getBytes().length + mClipboardPrefix.getBytes().length;
		}

		public String getString()
		{
			return mString;
		}

		public String getClipboardPrefix()
		{
			return mClipboardPrefix;
		}

		@Override
		public int sizeOf()
		{
			return mSize;
		}
	}

	// OTHER
	//================================================================================
	private Spannable getBoldedText(String text)
	{
		List<String> matching = new ArrayList<>();

		// find the strings that need to be bolded
		Pattern pattern = Pattern.compile( "<strong>(.+?)</strong>|<b>(.+?)</b>" );
		Matcher m = pattern.matcher(text);
		while (m.find())
		{
			if (m.group(1) != null)
				matching.add(m.group(1));
			else if (m.group(2) != null)
				matching.add(m.group(2));
		}

		// remove html
		text = text.replace("<strong>", "").replace("</strong>", "").replace("<b>", "").replace("</b>", "");

		// style the strings
		Spannable sb = new SpannableString(text);
		for (String string : matching)
		{
			sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), text.indexOf(string), string.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}

		return sb;
	}
}
