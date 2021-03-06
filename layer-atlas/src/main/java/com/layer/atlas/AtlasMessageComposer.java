/*
 * Copyright (c) 2015 Layer. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.layer.atlas;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.layer.atlas.messagetypes.AttachmentSender;
import com.layer.atlas.messagetypes.MessageSender;
import com.layer.atlas.messagetypes.text.TextSender;
import com.layer.atlas.provider.ParticipantProvider;
import com.layer.atlas.util.EditTextUtil;
import com.layer.sdk.LayerClient;
import com.layer.sdk.listeners.LayerTypingIndicatorListener;
import com.layer.sdk.messaging.Conversation;

import java.util.ArrayList;

public class AtlasMessageComposer extends FrameLayout 
{
    private EditText mMessageEditText;
    private Button mSendButton;
    private ImageView mAttachButton;
    private AttachmentDownListener mAttachmentDownListener;

    private LayerClient mLayerClient;
    private ParticipantProvider mParticipantProvider;
    private Conversation mConversation;

    private TextSender mTextSender;
    private ArrayList<AttachmentSender> mAttachmentSenders = new ArrayList<AttachmentSender>();
    private MessageSender.Callback mMessageSenderCallback;

    private PopupWindow mAttachmentMenu;
    private TextSentListener mTextSentListener;

    // styles
    private boolean mEnabled;
    private int mTextColor;
    private float mTextSize;
    private Typeface mTypeFace;
    private int mTextStyle;
    private int mUnderlineColor;
    private int mCursorColor;

    public void setAttachmentDownListener(AttachmentDownListener listener)
    {
        mAttachmentDownListener = listener;
    }
    public void setTextSentListener(TextSentListener listener)
    {
        mTextSentListener = listener;
    }

    public void setAttachmentButtonEnabled(boolean isEnabled)
    {
        if (mAttachButton != null)
            mAttachButton.setEnabled(isEnabled);
    }

    public String getMessageText()
    {
        if (mMessageEditText != null)
            return mMessageEditText.getText().toString();
        return null;
    }
    public void setMessageText(String text)
    {
        mMessageEditText.setText(text, TextView.BufferType.EDITABLE);
    }

    public AtlasMessageComposer(Context context)
    {
        super(context);
        initAttachmentMenu(context, null, 0);
    }

    public AtlasMessageComposer(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public AtlasMessageComposer(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        parseStyle(context, attrs, defStyle);
        initAttachmentMenu(context, attrs, defStyle);
    }

    /**
     * Prepares this AtlasMessageComposer for use.
     *
     * @return this AtlasMessageComposer.
     */
    public AtlasMessageComposer init(LayerClient layerClient, ParticipantProvider participantProvider)
    {
        LayoutInflater.from(getContext()).inflate(com.layer.atlas.R.layout.atlas_message_composer, this);

        mLayerClient = layerClient;
        mParticipantProvider = participantProvider;

        mAttachButton = (ImageView) findViewById(com.layer.atlas.R.id.attachment);
        mAttachButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(View v)
            {

                if(mAttachmentMenu.isShowing())
                {
                    mAttachmentMenu.dismiss();
                }
                else
                {
                    LinearLayout menu = (LinearLayout) mAttachmentMenu.getContentView();
                    menu.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    mAttachmentMenu.showAsDropDown(v, 0, -menu.getMeasuredHeight() - v.getHeight());
                }
            }
        });

        mMessageEditText = (EditText) findViewById(com.layer.atlas.R.id.message_edit_text);
        mMessageEditText.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }

            @Override
            public void afterTextChanged(Editable s)
            {
                if (mConversation == null || mConversation.isDeleted())
                    return;

                if (s.length() > 0)
                {
                    mSendButton.setEnabled(isEnabled());
                    mConversation.send(LayerTypingIndicatorListener.TypingIndicator.STARTED);
                }
                else
                {
                    mSendButton.setEnabled(false);
                    mConversation.send(LayerTypingIndicatorListener.TypingIndicator.FINISHED);
                }
            }
        });

        mMessageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener()
        {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
            {
                if (actionId == EditorInfo.IME_ACTION_DONE || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                {
                    return sendText();
                }

                return false;
            }
        });

        mSendButton = (Button) findViewById(com.layer.atlas.R.id.send_button);
        mSendButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(View v)
            {
                sendText();
            }
        });

        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener()
        {
            @Override
            public void onGlobalLayout()
            {
                int heightDiff = getRootView().getHeight() - getHeight();
                // if more than 100 pixels, its probably a keyboard...
                if (heightDiff > 100 && mAttachmentMenu.isShowing())
                {
                    mAttachmentMenu.dismiss();
                }
            }
        });

        applyStyle();
        return this;
    }

    private boolean sendText()
    {
        String text = mMessageEditText.getText().toString().trim();
        if (!mTextSender.requestSend(text))
            return false;

        mMessageEditText.setText("");
        mSendButton.setEnabled(false);

        if(mTextSentListener != null)
            mTextSentListener.onTextSent(text);

        return true;
    }

    public void setInputType(int inputType)
    {
        mMessageEditText.setInputType(inputType);
    }

    public void setHint(String hint)
    {
        mMessageEditText.setHint(hint);
    }

    /**
     * Sets the Conversation used for sending Messages.
     *
     * @param conversation the Conversation used for sending Messages.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer setConversation(Conversation conversation)
    {
        mConversation = conversation;
        if (mTextSender != null) mTextSender.setConversation(conversation);
        for (AttachmentSender sender : mAttachmentSenders)
        {
            sender.setConversation(conversation);
        }
        return this;
    }

    /**
     * Sets a listener for receiving the message EditText focus change callbacks.
     *
     * @param listener Listener for receiving the message EditText focus change callbacks.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer setOnMessageEditTextFocusChangeListener(OnFocusChangeListener listener)
    {
        mMessageEditText.setOnFocusChangeListener(listener);
        return this;
    }

    /**
     * Sets the TextSender used for sending composed text messages.
     *
     * @param textSender TextSender used for sending composed text messages.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer setTextSender(TextSender textSender)
    {
        mTextSender = textSender;
        mTextSender.init(this.getContext().getApplicationContext(), mLayerClient, mParticipantProvider);
        mTextSender.setConversation(mConversation);
        if (mMessageSenderCallback != null)
            mTextSender.setCallback(mMessageSenderCallback);

        return this;
    }

    /**
     * Adds AttachmentSenders to this AtlasMessageComposer's attachment menu.
     *
     * @param senders AttachmentSenders to add to this AtlasMessageComposer's attachment menu.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer addAttachmentSenders(AttachmentSender... senders)
    {
        for (AttachmentSender sender : senders)
        {
            if (sender.getTitle() == null && sender.getIcon() == null)
            {
                throw new NullPointerException("Attachment handlers must have at least a title or icon specified.");
            }
            sender.init(this.getContext().getApplicationContext(), mLayerClient, mParticipantProvider);
            sender.setConversation(mConversation);
            if (mMessageSenderCallback != null)
                sender.setCallback(mMessageSenderCallback);

            mAttachmentSenders.add(sender);
            addAttachmentMenuItem(sender);
        }
        if (!mAttachmentSenders.isEmpty())
            mAttachButton.setVisibility(View.VISIBLE);

        return this;
    }

    /**
     * Sets an optional callback for receiving MessageSender events.  If non-null, overrides any
     * callbacks already set on MessageSenders.
     *
     * @param callback Callback to receive MessageSender events.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer setMessageSenderCallback(MessageSender.Callback callback)
    {
        mMessageSenderCallback = callback;
        if (mMessageSenderCallback == null)
            return this;

        if (mTextSender != null)
            mTextSender.setCallback(callback);

        for (AttachmentSender sender : mAttachmentSenders)
        {
            sender.setCallback(callback);
        }
        return this;
    }

    public AtlasMessageComposer setTypeface(Typeface typeface)
    {
        this.mTypeFace = typeface;
        applyTypeface();
        return this;
    }

    /**
     * Must be called from Activity's onActivityResult to allow attachment senders to manage results
     * from e.g. selecting a gallery photo or taking a camera image.
     *
     * @param activity    Activity receiving the result.
     * @param requestCode Request code from the Activity's onActivityResult.
     * @param resultCode  Result code from the Activity's onActivityResult.
     * @param data        Intent data from the Activity's onActivityResult.
     * @return this AtlasMessageComposer.
     */
    public AtlasMessageComposer onActivityResult(Activity activity, int requestCode, int resultCode, Intent data)
    {
        for (AttachmentSender sender : mAttachmentSenders)
        {
            sender.onActivityResult(activity, requestCode, resultCode, data);
        }
        return this;
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);

        if (mAttachButton != null)
            mAttachButton.setEnabled(enabled);

        if (mMessageEditText != null)
            mMessageEditText.setEnabled(enabled);

        if (mSendButton != null)
            mSendButton.setEnabled(enabled && (mMessageEditText != null) && (mMessageEditText.getText().length() > 0));
    }

    private void parseStyle(Context context, AttributeSet attrs, int defStyle)
    {
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, com.layer.atlas.R.styleable.AtlasMessageComposer, com.layer.atlas.R.attr.AtlasMessageComposer, defStyle);
        mEnabled = ta.getBoolean(com.layer.atlas.R.styleable.AtlasMessageComposer_android_enabled, true);
        this.mTextColor = ta.getColor(com.layer.atlas.R.styleable.AtlasMessageComposer_inputTextColor, ContextCompat.getColor(context, com.layer.atlas.R.color.atlas_text_black));
        this.mTextSize = ta.getDimensionPixelSize(com.layer.atlas.R.styleable.AtlasMessageComposer_inputTextSize, context.getResources().getDimensionPixelSize(com.layer.atlas.R.dimen.atlas_text_size_input));
        this.mTextStyle = ta.getInt(com.layer.atlas.R.styleable.AtlasMessageComposer_inputTextStyle, Typeface.NORMAL);
        String typeFaceName = ta.getString(com.layer.atlas.R.styleable.AtlasMessageComposer_inputTextTypeface);
        this.mTypeFace = typeFaceName != null ? Typeface.create(typeFaceName, mTextStyle) : null;
        this.mUnderlineColor = ta.getColor(com.layer.atlas.R.styleable.AtlasMessageComposer_inputUnderlineColor, context.getResources().getColor(com.layer.atlas.R.color.atlas_color_primary_blue));
        this.mCursorColor = ta.getColor(com.layer.atlas.R.styleable.AtlasMessageComposer_inputCursorColor, ContextCompat.getColor(context, com.layer.atlas.R.color.atlas_color_primary_blue));
        ta.recycle();
    }

    private void applyStyle()
    {
        setEnabled(mEnabled);

        mMessageEditText.setTextColor(mTextColor);
        mMessageEditText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        EditTextUtil.setCursorDrawableColor(mMessageEditText, mCursorColor);
        EditTextUtil.setUnderlineColor(mMessageEditText, mUnderlineColor);
        applyTypeface();

        ColorStateList list = ContextCompat.getColorStateList(getContext(), R.color.atlas_message_composer_attach_button);
        Drawable d = DrawableCompat.wrap(mAttachButton.getDrawable().mutate());
        DrawableCompat.setTintList(d, list);
        mAttachButton.setImageDrawable(d);
    }

    private void applyTypeface()
    {
        mMessageEditText.setTypeface(mTypeFace, mTextStyle);
    }

    private void addAttachmentMenuItem(AttachmentSender sender)
    {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        LinearLayout menuLayout = (LinearLayout) mAttachmentMenu.getContentView();

        View menuItem = inflater.inflate(com.layer.atlas.R.layout.atlas_message_composer_attachment_menu_item, menuLayout, false);
        ((TextView) menuItem.findViewById(com.layer.atlas.R.id.title)).setText(sender.getTitle());
        menuItem.setTag(sender);
        menuItem.setOnClickListener(new OnClickListener()
        {
            public void onClick(View v)
            {
                mAttachmentMenu.dismiss();
                if(mAttachmentDownListener != null)
                {
                    mAttachmentDownListener.onAttachmentDown(((AttachmentSender) v.getTag()));
                }
                else
                {
                    ((AttachmentSender) v.getTag()).requestSend();
                }
            }
        });
        if (sender.getIcon() != null)
        {
            ImageView iconView = ((ImageView) menuItem.findViewById(com.layer.atlas.R.id.icon));
            iconView.setImageResource(sender.getIcon());
            iconView.setVisibility(VISIBLE);
            Drawable d = DrawableCompat.wrap(iconView.getDrawable());
            DrawableCompat.setTint(d, getResources().getColor(com.layer.atlas.R.color.atlas_icon_enabled));
        }
        menuLayout.addView(menuItem);
    }

    private void initAttachmentMenu(Context context, AttributeSet attrs, int defStyle)
    {
        if (mAttachmentMenu != null)
            throw new IllegalStateException("Already initialized menu");

        if (attrs == null)
        {
            mAttachmentMenu = new PopupWindow(context);
        }
        else
        {
            mAttachmentMenu = new PopupWindow(context, attrs, defStyle);
        }
        View contentView = LayoutInflater.from(context).inflate(com.layer.atlas.R.layout.atlas_message_composer_attachment_menu, null);
        contentView.setBackgroundResource(R.drawable.attachment_menu_background);
        mAttachmentMenu.setContentView(contentView);
        mAttachmentMenu.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        mAttachmentMenu.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected Parcelable onSaveInstanceState()
    {
        Parcelable superState = super.onSaveInstanceState();
        if (mAttachmentSenders.isEmpty())
            return superState;

        SavedState savedState = new SavedState(superState);
        for (AttachmentSender sender : mAttachmentSenders)
        {
            Parcelable parcelable = sender.onSaveInstanceState();
            if (parcelable == null)
                continue;

            savedState.put(sender.getClass(), parcelable);
        }
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state)
    {
        if (!(state instanceof SavedState))
        {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());

        for (AttachmentSender sender : mAttachmentSenders)
        {
            Parcelable parcelable = savedState.get(sender.getClass());
            if (parcelable == null)
                continue;

            sender.onRestoreInstanceState(parcelable);
        }
    }

    /**
     * Saves a map from AttachmentSender class to AttachmentSender saved instance.
     */
    private static class SavedState extends BaseSavedState
    {
        Bundle mBundle = new Bundle();

        public SavedState(Parcelable superState)
        {
            super(superState);
        }

        SavedState put(Class<? extends AttachmentSender> cls, Parcelable parcelable)
        {
            mBundle.putParcelable(cls.getName(), parcelable);
            return this;
        }

        Parcelable get(Class<? extends AttachmentSender> cls)
        {
            return mBundle.getParcelable(cls.getName());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags)
        {
            super.writeToParcel(dest, flags);
            dest.writeBundle(mBundle);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>()
        {
            public SavedState createFromParcel(Parcel in)
            {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size)
            {
                return new SavedState[size];
            }
        };

        private SavedState(Parcel in)
        {
            super(in);
            mBundle = in.readBundle();
        }
    }

    public interface AttachmentDownListener
    {
        void onAttachmentDown(AttachmentSender attachmentSender);
    }

    public interface TextSentListener
    {
        void onTextSent(String text);
    }
}

