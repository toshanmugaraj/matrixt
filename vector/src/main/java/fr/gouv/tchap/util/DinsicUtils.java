/*
 * Copyright 2018 DINSIC
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.gouv.tchap.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.Toast;
import androidx.fragment.app.FragmentActivity;

import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.MXPatterns;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomEmailInvitation;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.rest.model.CreateRoomParams;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomCreateContent;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.pid.RoomThirdPartyInvite;
import org.matrix.androidsdk.rest.model.pid.ThreePid;
import org.matrix.androidsdk.core.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fr.gouv.tchap.sdk.rest.client.TchapThirdPidRestClient;
import fr.gouv.tchap.sdk.session.room.model.RoomAccessRulesKt;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorAppCompatActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.util.RoomUtils;

public class DinsicUtils {
    private static final String LOG_TAG = "DinsicUtils";

    /**
     * Capitalize a string
     */
    public static String capitalize(String input) {
        StringBuilder builder = new StringBuilder();
        if (!input.isEmpty()) {
            builder.append(input.substring(0, 1).toUpperCase());
            if (input.length() > 1) {
                builder.append(input.substring(1));
            }
        }
        return builder.toString();
    }

    /**
     *  Returns the consumer friendly device name
     */
    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER.trim();
        String model = Build.MODEL.trim();
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    /**
     * Get the homeserver name of a matrix identifier.
     * The identifier type may be any matrix identifier type: user id, room id, ...
     * For example in case of "@jean-philippe.martin-modernisation.fr:matrix.test.org", this will return "matrix.test.org".
     * in case of "!AAAAAAA:matrix.test.org", this will return "matrix.test.org".
     */
    @Nullable
    public static String getHomeServerNameFromMXIdentifier(String mxId) {
        String result = null;
        if (mxId.contains(":")) {
            result = mxId.substring(mxId.indexOf(":") + 1);
        }
        return result;
    }

    /**
     * Get the Tchap display name of the homeserver mentioned in a matrix identifier.
     * The identifier type may be any matrix identifier type: user id, room id, ...
     * The returned name is capitalize.
     * The Tchap HS display name is the component mentioned before the suffix "tchap.gouv.fr"
     * For example in case of "@jean-philippe.martin-modernisation.fr:name1.tchap.gouv.fr", this will return "Name1".
     * in case of "@jean-philippe.martin-modernisation.fr:agent.name2.tchap.gouv.fr", this will return "Name2".
     */
    @Nullable
    public static String getHomeServerDisplayNameFromMXIdentifier(String mxId) {
        String result = null;
        String homeserverName = DinsicUtils.getHomeServerNameFromMXIdentifier(mxId);
        if (homeserverName != null) {
            if (homeserverName.contains("tchap.gouv.fr")) {
                String[] components = homeserverName.split("\\.");
                if (components.length >= 4) {
                    homeserverName = components[components.length - 4];
                }
            }
            // Capitalize the domain
            result = capitalize(homeserverName);
        }
        return result;
    }

    /**
     * Get name part of a display name by removing the domain part if any.
     * For example in case of "Jean Martin [Modernisation]", this will return "Jean Martin".
     *
     * @param displayName
     * @return displayName without domain (null if the provided display name is null).
     */
    @Nullable
    public static String getNameFromDisplayName(@Nullable String displayName) {
        String myRet = displayName;
        if (null != displayName && displayName.contains("[")) {
            myRet = displayName.split("\\[")[0].trim();
        }
        return myRet;
    }

    /**
     * Get the potential domain name from a display name.
     * For example in case of "Jean Martin [Modernisation]", this will return "Modernisation".
     *
     * @param displayName
     * @return displayName without name, null if no domain is available.
     */
    @Nullable
    public static String getDomainFromDisplayName(@Nullable String displayName) {
        String myRet = null;
        if (null != displayName && displayName.contains("[")) {
            myRet = displayName.split("\\[")[1];
            if (myRet.contains("]")) {
                myRet = myRet.split("\\]")[0].trim();
            } else {
                myRet = null;
            }
        }
        return myRet;
    }

    /**
     * Build a display name from the tchap user identifier.
     * We don't extract the domain for the moment in order to not display unexpected information.
     * For example in case of "@jean-philippe.martin-modernisation.fr:matrix.org", this will return "Jean-Philippe Martin".
     * Note: in case of an external user identifier, we return the local part of the id which corresponds to their email.
     *
     * @param tchapUserId user id
     * @return displayName without domain, null if the id is not valid.
     */
    @Nullable
    public static String computeDisplayNameFromUserId(@Nullable String tchapUserId) {
        String displayName = null;

        if (null != tchapUserId && MXPatterns.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER.matcher(tchapUserId).matches()) {
            // Remove first the host from the id by ignoring the first character '@' too.
            String identifier = tchapUserId.substring(1, tchapUserId.indexOf(":"));

            if (isExternalTchapUser(tchapUserId)) {
                // Replace the '-' character if there is only one
                if (identifier.split("-").length == 2) {
                    displayName = identifier.replace("-", "@");
                } else {
                    displayName = identifier;
                }
            } else {
                int index = identifier.lastIndexOf("-");
                if (-1 != index) {
                    // Retrieve the user name
                    displayName = identifier.substring(0, index);
                    String[] components = displayName.split("\\.");

                    StringBuilder builder = new StringBuilder();
                    for (String component : components) {
                        if (!component.isEmpty()) {
                            if (builder.length() > 0) {
                                // Add space between components
                                builder.append(" ");
                            }

                            // Check whether the component contains some '-'
                            if (component.contains("-")) {
                                // Capitalize each sub component
                                String[] subComponents = component.split("-");
                                for (int i = 0; i < subComponents.length - 1; i++) {
                                    String subComponent = subComponents[i];
                                    builder.append(subComponent.substring(0, 1).toUpperCase());
                                    if (subComponent.length() > 1) {
                                        builder.append(subComponent.substring(1));
                                    }
                                    builder.append("-");
                                }
                                // Retrieve the last sub-component
                                component = subComponents[subComponents.length - 1];
                            }

                            // Capitalize the component (or the last sub-component)
                            builder.append(component.substring(0, 1).toUpperCase());
                            if (component.length() > 1) {
                                builder.append(component.substring(1));
                            }
                        }
                    }
                    displayName = builder.toString();

                    // TODO: decide if we should append the extracted domain. This is not relevant for
                    // the moment because of the test users.
                /*// add first term of domain
                components = identifier.split("-");
                if (components.length > 1) {
                    index = components[components.length - 1].indexOf(".");
                    if (-1 != index) {
                        String domain = components[components.length - 1].substring(0, index);
                        if (domain.length() > 0) {
                            String formattedDomain = domain.substring(0, 1).toUpperCase();
                            if (domain.length() > 1) {
                                formattedDomain += domain.substring(1);
                            }
                            displayName += " ["+formattedDomain+"]";
                        }
                    }
                }*/
                }
            }
        }

        return displayName;
    }

    /**
     * Tells whether a session corresponds to an external user or not
     *
     * @param session
     * @return true if external
     */
    public static boolean isExternalTchapSession(MXSession session) {
        String myHost = session.getHomeServerConfig().getHomeserverUri().getHost();
        return (myHost.contains(".e.") || myHost.contains(".externe."));
    }

    /**
     * Tells whether the provided tchap identifier corresponds to an extern user.
     * Note: invalid tchap identifier will be considered as external.
     *
     * @param tchapUserId user id
     * @return true if external
     */
    public static boolean isExternalTchapUser(String tchapUserId) {
        String homeServerName = getHomeServerNameFromMXIdentifier(tchapUserId);
        if (homeServerName != null) {
            return isExternalTchapServer(homeServerName);
        }
        return true;
    }

    /**
     * Tells whether a homeserver name corresponds to an external server or not
     *
     * @param homeServerName
     * @return true if external
     */
    public static boolean isExternalTchapServer(@NonNull String homeServerName) {
        return (homeServerName.startsWith("e.") || homeServerName.startsWith("agent.externe."));
    }

    /**
     * Edit contact form
     */
    public static void editContactForm(Context theContext, Activity myActivity, String editContactWarningMsg, final Contact contact ) {
        LayoutInflater inflater = LayoutInflater.from(theContext);

        Cursor namesCur=null;
        boolean switchToContact=false;
        try
        {
            ContentResolver cr = theContext.getContentResolver();
            namesCur = cr.query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                            ContactsContract.Contacts.LOOKUP_KEY,
                            ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID
                    },
                    ContactsContract.Data.MIMETYPE + " = ? AND "+ ContactsContract.Data.CONTACT_ID + " = ?",

                    new String[]{ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                            contact.getContactId()}, null);
            if (namesCur != null) {
                if (namesCur.moveToNext()) {
                    String contactId = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID));
                    Uri mSelectedContactUri;
                    int mLookupKeyIndex;
                    int mIdIndex;
                    String mCurrentLookupKey;
                    mLookupKeyIndex = namesCur.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
                    mCurrentLookupKey = namesCur.getString(mLookupKeyIndex);
                    //mCursor.getColumnIndex(ContactsContract.Contacts._ID);
                    long mCurrentId = Integer.parseInt(contactId);//namesCur.getLong(mIdIndex);
                    mSelectedContactUri =
                            ContactsContract.Contacts.getLookupUri(mCurrentId, mCurrentLookupKey);


                    Intent editIntent = new Intent(Intent.ACTION_EDIT);
                    editIntent.setDataAndType(mSelectedContactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                    editIntent.putExtra("finishActivityOnSaveCompleted", true);
                    myActivity.startActivity(editIntent);
                    switchToContact=true;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## editContactForm(): Exception - Msg=" + e.getMessage());
        }
        if (!switchToContact){
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(myActivity);
            alertDialogBuilder.setMessage(editContactWarningMsg);

            // set dialog message
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok,null);

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();
            // show it
            alertDialog.show();

        }
    }

    public static void editContact(final FragmentActivity activity, final Context theContext, final ParticipantAdapterItem item) {
        if (ContactsManager.getInstance().isContactBookAccessAllowed()) {
            //enterEmailAddress(item.mContact);
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
            alertDialogBuilder.setMessage(activity.getString(R.string.people_invalid_warning_msg));
            // set dialog message
            alertDialogBuilder
                    .setNegativeButton(R.string.cancel,null)
                    .setPositiveButton(R.string.action_edit_contact_form,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    editContactForm(theContext,activity,activity.getString(R.string.people_edit_contact_warning_msg),item.mContact);
                                }
                            });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();
            // show it
            alertDialog.show();

        }
        else {

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
            alertDialogBuilder.setMessage(activity.getString(R.string.people_invalid_warning_msg));

            // set dialog message
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {

                                }
                            });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();
            // show it
            alertDialog.show();

        }
    }

    public static boolean participantAlreadyAdded(List<ParticipantAdapterItem> participants, ParticipantAdapterItem participant) {
        boolean find = false;
        Iterator<ParticipantAdapterItem> iterator = participants.iterator();
        boolean finish = !iterator.hasNext();
        while (!finish) {
            ParticipantAdapterItem curp = iterator.next();
            if (curp != null && curp.mIsValid)
                find = curp.mUserId.equals(participant.mUserId);
            finish = (find || !(iterator.hasNext()));
        }

        return find;

    }

    public static boolean removeParticipantIfExist(List<ParticipantAdapterItem> participants, ParticipantAdapterItem participant) {

        boolean find = false;
        Iterator<ParticipantAdapterItem> iterator = participants.iterator();
        boolean finish = !iterator.hasNext();
        while (!finish) {
            ParticipantAdapterItem curp = iterator.next();
            if (curp != null && curp.mIsValid) {
                find = curp.mUserId.equals(participant.mUserId);
                if (find) iterator.remove();
            }
            finish = (find || !(iterator.hasNext()));
        }

        return find;
    }

    public static void alertSimpleMsg(FragmentActivity activity, String msg) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
        alertDialogBuilder.setMessage(msg);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();
        // show it
        alertDialog.show();

    }

    //=============================================================================================
    // Handle existing direct chat room
    //=============================================================================================

    /**
     * Select the most suitable direct chat for the provided user identifier (matrix or third party id).
     * During this search, the pending invite are considered too.
     * The selected room (if any) may be opened synchronously or not. It depends if the room is ready or not.
     *
     * @param activity current activity
     * @param participantId : participant id (matrix id ou email)
     * @param session current session
     * @param canCreate create the direct chat if it does not exist.
     * @return boolean that says if the direct chat room is found or not
     */
    public static boolean openDirectChat(final VectorAppCompatActivity activity, String participantId, final MXSession session, boolean canCreate) {
        Room existingRoom = isDirectChatRoomAlreadyExist(participantId, session, true);
        boolean succeeded = false;

        // direct message api callback
        ApiCallback<String> prepareDirectChatCallBack = new ApiCallback<String>() {
            @Override
            public void onSuccess(final String roomId) {
                activity.hideWaitingView();

                HashMap<String, Object> params = new HashMap<>();
                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, session.getMyUserId());
                params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                params.put(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);

                Log.d(LOG_TAG, "## prepareDirectChatCallBack: onSuccess - start goToRoomPage");
                CommonActivityUtils.goToRoomPage(activity, session, params);
            }

            private void onError(final String message) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != message) {
                            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                        }
                        activity.hideWaitingView();
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError(e.getLocalizedMessage());
            }
        };

        if (null != existingRoom) {
            // If I am invited to this room, I accept invitation and join it
            if (existingRoom.isInvited()) {
                succeeded = true;
                activity.showWaitingView();
                session.joinRoom(existingRoom.getRoomId(), prepareDirectChatCallBack);
            } else {
                succeeded = true;
                HashMap<String, Object> params = new HashMap<>();
                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, participantId);
                params.put(VectorRoomActivity.EXTRA_ROOM_ID, existingRoom.getRoomId());
                CommonActivityUtils.goToRoomPage(activity, session, params);
            }
        } else if (canCreate){
            // direct message flow
            //it will be more open on next sprints ...
            if (!DinsicUtils.isExternalTchapSession(session)) {
                succeeded = true;
                activity.showWaitingView();
                createDirectChat(session, participantId, prepareDirectChatCallBack);
            } else {
                alertSimpleMsg(activity, activity.getString(R.string.room_creation_forbidden));
            }
        }
        return succeeded;
    }

    /**
     * Return the first direct chat room for a given user ID.
     *
     * @param aUserId user ID to search for
     * @param mSession current session
     * @param includeInvite boolean to tell us if pending invitations have to be consider or not
     * @return a room ID if search succeed, null otherwise.
     */
    public static Room isDirectChatRoomAlreadyExist(String aUserId, MXSession mSession, boolean includeInvite) {
        if (null != mSession) {
            IMXStore store = mSession.getDataHandler().getStore();
            HashMap<String, List<String>> directChatRoomsDict;

            if (null != store.getDirectChatRoomsDict()) {
                directChatRoomsDict = new HashMap<>(store.getDirectChatRoomsDict());

                if (directChatRoomsDict.containsKey(aUserId)) {
                    ArrayList<String> roomIdsList = new ArrayList<>(directChatRoomsDict.get(aUserId));

                    if (!roomIdsList.isEmpty()) {
                        // In the description of the memberships, we display first the current user status and the other member in second.
                        // We review all the direct chats by considering the memberships in the following priorities :
                        // 1. join-join
                        // 2. invite-join
                        // 3. join-invite
                        // 4. join-left (or invite-left)
                        // The case left-x isn't possible because we ignore for the moment the left rooms.
                        Room roomCandidateLeftByOther = null;
                        Room roomCandidatePendingInvite = null;
                        boolean isPendingInvite = false;

                        for (String roomId : roomIdsList) {
                            Room room = mSession.getDataHandler().getRoom(roomId, false);
                            // check if the room is already initialized
                            if ((null != room) && room.isReady() && !room.isLeaving()) {
                                isPendingInvite = room.isInvited();
                                if (includeInvite || !isPendingInvite) {
                                    // dinsic: if the member is not already in matrix and just invited he's not active but
                                    // the room can be considered as ok
                                    if (!MXPatterns.isUserId(aUserId)) {
                                        Log.d(LOG_TAG, "## isDirectChatRoomAlreadyExist(): for user: " + aUserId + " room id: " + roomId);
                                        return room;
                                    } else {
                                        RoomMember member = room.getMember(aUserId);

                                        if (null != member) {
                                            if (member.membership.equals(RoomMember.MEMBERSHIP_JOIN)) {
                                                if (!isPendingInvite) {
                                                    // the other user is present in this room (join-join)
                                                    Log.d(LOG_TAG, "## isDirectChatRoomAlreadyExist(): for user: " + aUserId + " (join) room id: " + roomId);
                                                    return room;
                                                } else {
                                                    // I am invited by the other member (invite-join)
                                                    // We consider first de case "invite-join" compare to "join-invite"
                                                    Log.d(LOG_TAG, "## isDirectChatRoomAlreadyExist(): set candidate (invite-join) room id: " + roomId);
                                                    roomCandidatePendingInvite = room;
                                                }
                                            } else if (member.membership.equals(RoomMember.MEMBERSHIP_INVITE)) {
                                                // the other user is invited (join-invite)
                                                if (roomCandidatePendingInvite == null) {
                                                    Log.d(LOG_TAG, "## isDirectChatRoomAlreadyExist(): set candidate (join-invite) room id: " + roomId);
                                                    roomCandidatePendingInvite = room;
                                                }
                                            } else if (member.membership.equals(RoomMember.MEMBERSHIP_LEAVE)) {
                                                // the other member has left this room
                                                // and I can be invite or join
                                                Log.d(LOG_TAG, "## isDirectChatRoomAlreadyExist(): set candidate (join-left) room id: " + roomId);
                                                roomCandidateLeftByOther = room;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // check if an invitation is pending
                        if (null != roomCandidatePendingInvite) {
                            Log.d(LOG_TAG, "## isDirectChatRoomAlreadyExist(): user: " + aUserId + " (invite) room id: " + roomCandidatePendingInvite.getRoomId());
                            return roomCandidatePendingInvite;
                        }

                        // by default we consider the room left by the other member
                        if (null != roomCandidateLeftByOther) {
                            Log.d(LOG_TAG, "## isDirectChatRoomAlreadyExist(): user: " + aUserId + " (leave) room id: " + roomCandidateLeftByOther.getRoomId());
                            return roomCandidateLeftByOther;
                        }
                    }
                }
            }
        }
        Log.d(LOG_TAG, "## isDirectChatRoomAlreadyExist(): for user=" + aUserId + " no found room");
        return null;
    }

    /**
     * Prepare a direct chat with a selected contact.
     *
     * @param activity  the current activity
     * @param session   the current session
     * @param selectedContact the selected contact
     */
    public static void startDirectChat (final VectorAppCompatActivity activity, final MXSession session, final ParticipantAdapterItem selectedContact) {
        if (selectedContact.mIsValid) {
            // Tell if contact is tchap user
            if (MXPatterns.isUserId(selectedContact.mUserId)) {
                // The contact is a Tchap user, try to get the corresponding User instance.
                User tchapUser = session.getDataHandler().getUser(selectedContact.mUserId);
                // The return value is null if we don't already share a room with him.
                if (null == tchapUser) {
                    tchapUser = new User();
                    tchapUser.user_id = selectedContact.mUserId;
                    tchapUser.avatar_url = selectedContact.mAvatarUrl;
                    tchapUser.displayname = selectedContact.mDisplayName;
                } else {
                    // Check whether we know his display name (we may lost it if he left all our common rooms).
                    if (null == tchapUser.displayname) {
                        tchapUser.displayname = selectedContact.mDisplayName;
                    }
                    if (null == tchapUser.avatar_url) {
                        tchapUser.avatar_url = selectedContact.mAvatarUrl;
                    }
                }
                startDirectChat(activity, session, tchapUser);
            } else {
                // The contact isn't a Tchap user
                String msg = activity.getResources().getString(R.string.room_invite_people);

                if (!openDirectChat(activity, selectedContact.mUserId, session, false)) {
                    if (DinsicUtils.isExternalTchapSession(session)) {
                        alertSimpleMsg(activity, activity.getResources().getString(R.string.room_creation_forbidden));
                    } else {
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
                        alertDialogBuilder.setMessage(msg);

                        // set dialog message
                        alertDialogBuilder
                                .setCancelable(false)
                                .setPositiveButton(R.string.ok,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                openDirectChat(activity, selectedContact.mUserId, session, true);
                                            }
                                        })
                                .setNegativeButton(R.string.cancel, null);

                        // create alert dialog
                        AlertDialog alertDialog = alertDialogBuilder.create();
                        // show it
                        alertDialog.show();
                    }
                }
            }
        } else { // tell the user that the email must be filled. Propose to fill it
            editContact(activity, activity, selectedContact);
        }
    }

    /**
     * Prepare a direct chat with a tchap user.
     *
     * @param activity      the current activity
     * @param session       the current session
     * @param selectedUser  the selected tchap user
     */
    public static void startDirectChat(final VectorAppCompatActivity activity, final MXSession session, User selectedUser) {
        // Consider here that the provided id is a correct matrix identifier, we don't check again
        // Try first to open an existing direct chat
        if (!openDirectChat(activity, selectedUser.user_id, session, false)) {
            // There is no direct chat with him yet
            // Display a fake room, the actual room will be created on the first message
            HashMap<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, session.getMyUserId());

            // Sanity check: force a display name if it is undefined.
            if (null == selectedUser.displayname) {
                selectedUser.displayname = computeDisplayNameFromUserId(selectedUser.user_id);
            }
            params.put(VectorRoomActivity.EXTRA_TCHAP_USER, selectedUser);

            CommonActivityUtils.goToRoomPage(activity, session, params);
        }
    }

    /**
     * Create a direct chat room with one participant.<br>
     * The participant can be a user ID or mail address. Once the room is created, on success, the room
     * is set as a "direct message" with the participant.
     *
     * @param session            the current session
     * @param participantUserId  user ID (or user mail) to be invited in the direct message room
     * @param createRoomCallBack async call back response
     * @return true if the invite was performed, false otherwise
     */
    public static boolean createDirectChat(final MXSession session, final String participantUserId, final ApiCallback<String> createRoomCallBack) {
        boolean retCode = false;

        if (!TextUtils.isEmpty(participantUserId)) {
            retCode = true;
            CreateRoomParams params = new CreateRoomParams();

            params.setDirectMessage();
            params.addParticipantIds(session.getHomeServerConfig(), Arrays.asList(participantUserId));

            // Add the right room access rule
            Event roomAccessRulesEvent = new Event();
            roomAccessRulesEvent.type = RoomAccessRulesKt.STATE_EVENT_TYPE;
            Map<String, String> contentMap = new HashMap<>();
            contentMap.put(RoomAccessRulesKt.STATE_EVENT_CONTENT_KEY_RULE, RoomAccessRulesKt.DIRECT);
            roomAccessRulesEvent.updateContent(JsonUtils.getGson(false).toJsonTree(contentMap));
            roomAccessRulesEvent.stateKey = "";
            params.initialStates = Arrays.asList(roomAccessRulesEvent);

            session.createRoom(params, createRoomCallBack);
        }

        return retCode;
    }

    //=============================================================================================
    // Handle Rooms
    //=============================================================================================

    /**
     * Join a room described by an instance of the RoomPreviewData class.
     *
     * @param roomPreviewData the room preview data
     */
    public static void joinRoom(final RoomPreviewData roomPreviewData, final ApiCallback<Void> callback) {
        MXSession session = roomPreviewData.getSession();
        Room room = session.getDataHandler().getRoom(roomPreviewData.getRoomId());
        RoomEmailInvitation roomEmailInvitation = roomPreviewData.getRoomEmailInvitation();

        String signUrl = null;

        if (null != roomEmailInvitation) {
            signUrl = roomEmailInvitation.signUrl;
        }

        room.joinWithThirdPartySigned(roomPreviewData.getRoomIdOrAlias(), signUrl, callback);
    }

    /**
     * A new room has been joined.
     * - check whether this is a direct chat.
     * - open the room activity for this room.
     *
     * @param activity         the current activity. This activity is closed when the joined room is valid.
     * @param roomPreviewData  the room preview data
     */
    public static void onNewJoinedRoom(final Activity activity, final RoomPreviewData roomPreviewData) {
        final MXSession session = roomPreviewData.getSession();

        Room room = session.getDataHandler().getRoom(roomPreviewData.getRoomId());
        if (null != room) {
            // Prepare the room activity parameters.
            final String myUserId = session.getMyUserId();
            final HashMap<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, myUserId);
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomPreviewData.getRoomId());

            if (null != roomPreviewData.getEventId()) {
                params.put(VectorRoomActivity.EXTRA_EVENT_ID, roomPreviewData.getEventId());
            }

            // Check whether this new room must be added to the user's direct chats before opening it.
            if (getRoomAccessRule(room).equals(RoomAccessRulesKt.DIRECT) && !room.isDirect()) {
                Log.d(LOG_TAG, "## onNewJoinedRoom(): this new joined room is direct");
                CommonActivityUtils.setToggleDirectMessageRoom(session, roomPreviewData.getRoomId(), null, new SimpleApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        // Open the room activity for this room.
                        CommonActivityUtils.goToRoomPage(activity, session, params);
                    }
                });
            } else {
                // Open the room activity for this room.
                CommonActivityUtils.goToRoomPage(activity, session, params);
            }
        }
    }

    /**
     * Return the Dinsic rooms comparator. We display first the pinned rooms, then we sort them by date.
     *
     * @param session
     * @param reverseOrder
     * @return comparator
     */
    public static Comparator<Room> getRoomsComparator(final MXSession session, final boolean reverseOrder) {
        return new Comparator<Room>() {
            private Comparator<Room> mRoomsDateComparator;

            // Retrieve the default room comparator by date
            private Comparator<Room> getRoomsDateComparator() {
                if (null == mRoomsDateComparator) {
                    mRoomsDateComparator = RoomUtils.getRoomsDateComparator(session, reverseOrder);
                }
                return mRoomsDateComparator;
            }

            public int compare(Room room1, Room room2) {
                // Check first whether some rooms are pinned
                final Set<String> tagsRoom1 = room1.getAccountData().getKeys();
                final boolean isPinnedRoom1 = tagsRoom1 != null && tagsRoom1.contains(RoomTag.ROOM_TAG_FAVOURITE);
                final Set<String> tagsRoom2 = room2.getAccountData().getKeys();
                final boolean isPinnedRoom2 = tagsRoom2 != null && tagsRoom2.contains(RoomTag.ROOM_TAG_FAVOURITE);

                if (isPinnedRoom1 && !isPinnedRoom2) {
                    return reverseOrder ? 1 : -1;
                } else if (!isPinnedRoom1 && isPinnedRoom2) {
                    return reverseOrder ? -1 : 1;
                }
                // Consider the last message date to sort them
                return getRoomsDateComparator().compare(room1, room2);
            }
        };
    }

    /**
     * Compute the room display name.
     * Tchap client handles the display name of the direct chats with a different manner than the SDK one.
     *
     * @param context the application context.
     * @param room    the room.
     * @return the room display name.
     */
    @NonNull
    public static String getRoomDisplayName(Context context, @NonNull Room room) {
        // Retrieve the room display name without forcing a default one.
        String displayName = room.getRoomDisplayName(context, null);
        if (TextUtils.isEmpty(displayName)) {
            // Set the default display name.
            displayName = context.getString(R.string.room_displayname_empty_room);

            // Tchap - The display name of a direct chat must be the other member name
            // even if the member has left the room.
            if (room.isDirect()) {
                RoomState roomState = room.getState();
                Collection<RoomMember> members = roomState.getDisplayableLoadedMembers();
                for (RoomMember member : members) {
                    if (!TextUtils.equals(member.getUserId(), room.getDataHandler().getUserId())) {
                        displayName = member.getName();
                        break;
                    }
                }
            }
        } else if (MXPatterns.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER.matcher(displayName).matches()) {
            displayName = computeDisplayNameFromUserId(displayName);
        }

        return displayName;
    }

    /**
     * Tell whether users on other servers can join this room.
     *
     * @param room    the room.
     * @return true if this is a federated room.
     */
    public static boolean isFederatedRoom(@NonNull Room room) {
        RoomState roomState = room.getState();

        RoomCreateContent roomCreateContent = roomState.getRoomCreateContent();
        if (roomCreateContent != null && roomCreateContent.isFederated != null) {
            return roomCreateContent.isFederated;
        } else {
            // The room is federated by default
            return true;
        }
    }

    /**
     * Get the current room access rule.
     *
     * @param room    the room.
     * @return the room access rule see {@link RoomAccessRulesKt}.
     */
    @NonNull
    public static String getRoomAccessRule(@NonNull Room room) {
        RoomState roomState = room.getState();

        List<Event> roomAccessRulesEvents = roomState.getStateEvents(new HashSet<>(Arrays.asList(RoomAccessRulesKt.STATE_EVENT_TYPE)));
        String rule = null;

        if (roomAccessRulesEvents.isEmpty()) {
            Log.d(LOG_TAG, "## getRoomAccessRule(): no rule is defined");
        } else {
            Event roomAccessRulesEvent = roomAccessRulesEvents.get(roomAccessRulesEvents.size() - 1);

            // Sanity check: be sure to consider the most recent state event
            for (int index = 0; index < roomAccessRulesEvents.size() - 1; index ++) {
                Event event = roomAccessRulesEvents.get(index);
                if (roomAccessRulesEvent.originServerTs < event.originServerTs) {
                    roomAccessRulesEvent = event;
                }
            }

            rule = RoomAccessRulesKt.getRule(roomAccessRulesEvent);
            Log.d(LOG_TAG, "## getRoomAccessRule(): the rule " + rule + " is defined");
        }

        if (rule == null) {
            // Consider here the rooms without room access rule
            if (room.isDirect()) {
                rule = RoomAccessRulesKt.DIRECT;

//                // TODO or not here?: Add the corresponding state event in the room state
//                setRoomAccessRule(session, room, RoomAccessRulesKt.DIRECT, new ApiCallback<Void>() {
//                    @Override
//                    public void onSuccess(Void info) {
//                        Log.d(LOG_TAG, "## getRoomAccessRule(): the room access rules state event (direct) has been added in the room state");
//                    }
//
//                    private void onError(String errorMessage) {
//                        Log.e(LOG_TAG, "## getRoomAccessRule(): failed to add a room access rules state event " + errorMessage);
//                    }
//
//                    @Override
//                    public void onNetworkError(Exception e) {
//                        onError(e.getMessage());
//                    }
//
//                    @Override
//                    public void onMatrixError(MatrixError e) {
//                        onError(e.getMessage());
//                    }
//
//                    @Override
//                    public void onUnexpectedError(Exception e) {
//                        onError(e.getMessage());
//                    }
//                });

            } else {
                rule = RoomAccessRulesKt.RESTRICTED;
            }
        }

        return rule;
    }

    public static void setRoomAccessRule (MXSession session, Room room, @NonNull String rule, final ApiCallback<Void> callback) {

        Map<String, Object> content = new HashMap<>();
        content.put(RoomAccessRulesKt.STATE_EVENT_CONTENT_KEY_RULE, rule);

        session.getRoomsApiClient().sendStateEvent(room.getRoomId(), RoomAccessRulesKt.STATE_EVENT_TYPE, "", content, callback);
    }

    /**
     * Return the room avatar.
     * Tchap client handles the room avatar with a different manner than the SDK one:
     * - Do not use by default a member avatar for the room avatar, except for the direct chats.
     *
     * @param room    the room.
     * @return the room avatar url.
     */
    @Nullable
    public static String getRoomAvatarUrl(@NonNull Room room) {
        String avatarUrl;
        if (room.isDirect()) {
            avatarUrl = room.getAvatarUrl();
        } else {
            avatarUrl = room.getState().getAvatarUrl();
        }
        return avatarUrl;
    }

    /* get contacts from direct chats */
    public static List<ParticipantAdapterItem> getContactsFromDirectChats(final MXSession session) {
        List<ParticipantAdapterItem> participants = new ArrayList<>();

        if ((null == session) || (null == session.getDataHandler())) {
            Log.e(LOG_TAG, "## getContactsFromDirectChats() : null session");
            return participants;
        }

        IMXStore store = session.getDataHandler().getStore();

        if (null != store.getDirectChatRoomsDict()) {
            // Retrieve all the keys of the direct chats HashMap (they correspond to the users with direct chats)
            List<String> keysList = new ArrayList<>(store.getDirectChatRoomsDict().keySet());

            for (String key : keysList) {
                // Check whether this key is an actual user id
                if (MXPatterns.isUserId(key)) {
                    // Ignore the current user if he appears in the direct chat map
                    if (key.equals(session.getMyUserId())) {
                        continue;
                    }

                    // Retrieve the user display name from the room members information.
                    // By this way we check that the current user has joined at least one of the direct chats for this user.
                    // The users for whom no direct is joined by the current user are ignored for the moment.
                    // @TODO Keep displaying these users in the contacts list, the problem is to get their displayname
                    // @NOTE The user displayname may be known thanks to the presence event. But
                    // it is unknown until we receive a presence event for this user.

                    // @NOTE We decided to hide users who left all our direct chats to prevent the user from inviting
                    // a deactivated account user.
                    List<String> roomIdsList = store.getDirectChatRoomsDict().get(key);
                    if (roomIdsList != null && !roomIdsList.isEmpty()) {
                        for (String roomId: roomIdsList) {
                            Room room = store.getRoom(roomId);
                            if (null != room) {
                                RoomMember roomMember = room.getMember(key);
                                if (null != roomMember
                                        && (TextUtils.equals(roomMember.membership, RoomMember.MEMBERSHIP_JOIN) || TextUtils.equals(roomMember.membership, RoomMember.MEMBERSHIP_INVITE))
                                        && !TextUtils.isEmpty(roomMember.displayname)) {
                                    // Add a contact for this user
                                    Contact dummyContact = new Contact("null");
                                    dummyContact.setDisplayName(roomMember.displayname);
                                    ParticipantAdapterItem participant = new ParticipantAdapterItem(dummyContact);
                                    participant.mUserId = key;
                                    participants.add(participant);
                                    break;
                                }
                            }
                        }
                    }
                }
                else if (android.util.Patterns.EMAIL_ADDRESS.matcher(key).matches()) {
                    // Check here whether this email corresponds to an actual user id in order to
                    // update the direct chat rooms map (Use the proxied lookup API).
                    TchapThirdPidRestClient tchapThirdPidRestClient = new TchapThirdPidRestClient(session.getHomeServerConfig());
                    tchapThirdPidRestClient.lookup(key, ThreePid.MEDIUM_EMAIL, new ApiCallback<String>() {
                        @Override
                        public void onSuccess(final String mxId) {
                            Log.i(LOG_TAG, "## getContactsFromDirectChats: lookup success");

                            if (!TextUtils.isEmpty(mxId)) {
                                updateDirectChatRoomsOnDiscoveredUser(session, key, mxId);
                            }
                        }

                        private void onError(String errorMessage) {
                            Log.e(LOG_TAG, "## getContactsFromDirectChats: lookup failed " + errorMessage);
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            onError(e.getMessage());
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            onError(e.getMessage());
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            onError(e.getMessage());
                        }
                    });
                }
            }
        }

        return participants;
    }

    /**
     * Call this method when a user id is discovered for an email address present in the direct chat
     * rooms mapping. The mapping is updated by replacing the email with the actual user id.
     *
     * @param session the current session
     * @param email the email address to replace.
     * @param userId the discovered matrix identifier.
     */
    private static void updateDirectChatRoomsOnDiscoveredUser(final MXSession session, final String email, final String userId) {
        IMXStore store = session.getDataHandler().getStore();

        // Check whether the direct chat rooms map was not already updated
        if (!store.getDirectChatRoomsDict().containsKey(email)) {
            Log.v(LOG_TAG, "## updateDirectChatRoomsOnDiscoveredUser() direct chat map has been already updated");
            return;
        }

        // @TODO Add MXSession API to update the HashMap in one run.
        List<String> roomIdsList = new ArrayList<>(store.getDirectChatRoomsDict().get(email));
        for (final String roomId : roomIdsList) {
            Log.i(LOG_TAG, "## updateDirectChatRoomsOnDiscoveredUser() " + roomId);
            // Disable first the direct chat to set it on the right user id
            session.toggleDirectChatRoom(roomId, null, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    session.toggleDirectChatRoom(roomId, userId, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            Log.i(LOG_TAG, "## updateDirectChatRoomsOnDiscoveredUser() succeeded to update direct chat map ");
                            // @TODO we may add the resulting contact (but he may be already present)
                        }

                        private void onFails(final String errorMessage) {
                            Log.e(LOG_TAG, "## updateDirectChatRoomsOnDiscoveredUser() failed to update direct chat map " + errorMessage);
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            onFails(e.getLocalizedMessage());
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            onFails(e.getLocalizedMessage());
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            onFails(e.getLocalizedMessage());
                        }
                    });
                }

                private void onFails(final String errorMessage) {
                    Log.e(LOG_TAG, "## updateDirectChatRoomsOnDiscoveredUser() failed to update direct chat map " + errorMessage);
                }

                @Override
                public void onNetworkError(Exception e) {
                    onFails(e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onFails(e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onFails(e.getLocalizedMessage());
                }
            });
        }
    }
}
