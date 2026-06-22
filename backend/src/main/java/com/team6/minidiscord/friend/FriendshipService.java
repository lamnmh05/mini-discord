package com.team6.minidiscord.friend;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.Keys;
import com.team6.minidiscord.common.util.ObjectIds;
import com.team6.minidiscord.friend.dto.FriendRequestDirection;
import com.team6.minidiscord.friend.dto.FriendRequestResponse;
import com.team6.minidiscord.friend.dto.FriendResponse;
import com.team6.minidiscord.friend.dto.FriendUserResponse;
import com.team6.minidiscord.friend.dto.SendFriendRequest;
import com.team6.minidiscord.realtime.PresenceLookupService;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final PresenceLookupService presenceLookupService;

    public FriendshipService(
            FriendshipRepository friendshipRepository,
            UserRepository userRepository,
            PresenceLookupService presenceLookupService
    ) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.presenceLookupService = presenceLookupService;
    }

    public List<FriendResponse> listFriends(ObjectId userId) {
        List<FriendshipDocument> friendships = friendshipRepository.findByRequesterIdOrAddresseeId(userId, userId).stream()
                .filter(friendship -> friendship.status == FriendStatus.ACCEPTED)
                .toList();
        Map<ObjectId, FriendshipDocument> friendshipByUserId = friendships.stream()
                .collect(Collectors.toMap(friendship -> otherUserId(friendship, userId), Function.identity()));
        if (friendshipByUserId.isEmpty()) {
            return List.of();
        }
        return userRepository.findByIdIn(friendshipByUserId.keySet()).stream()
                .map(user -> {
                    FriendshipDocument friendship = friendshipByUserId.get(user.id);
                    return new FriendResponse(
                            friendship.id.toHexString(),
                            userResponse(user),
                            friendship.respondedAt
                    );
                })
                .sorted(Comparator.comparing(response -> response.user().username(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<FriendRequestResponse> listRequests(ObjectId userId) {
        List<FriendshipDocument> requests = new ArrayList<>();
        requests.addAll(friendshipRepository.findByAddresseeIdAndStatusOrderByRequestedAtDesc(userId, FriendStatus.PENDING));
        requests.addAll(friendshipRepository.findByRequesterIdAndStatusOrderByRequestedAtDesc(userId, FriendStatus.PENDING));
        Map<ObjectId, FriendshipDocument> byOtherUser = requests.stream()
                .collect(Collectors.toMap(friendship -> otherUserId(friendship, userId), Function.identity(), (left, right) -> left));
        if (byOtherUser.isEmpty()) {
            return List.of();
        }
        Map<ObjectId, UserDocument> usersById = userRepository.findByIdIn(byOtherUser.keySet()).stream()
                .collect(Collectors.toMap(user -> user.id, Function.identity()));
        return requests.stream()
                .map(friendship -> {
                    ObjectId otherUserId = otherUserId(friendship, userId);
                    UserDocument user = usersById.get(otherUserId);
                    if (user == null) {
                        return null;
                    }
                    return new FriendRequestResponse(
                            friendship.id.toHexString(),
                            userResponse(user),
                            friendship.addresseeId.equals(userId) ? FriendRequestDirection.INCOMING : FriendRequestDirection.OUTGOING,
                            friendship.requestedAt
                    );
                })
                .filter(response -> response != null)
                .sorted(Comparator.comparing(FriendRequestResponse::requestedAt).reversed())
                .toList();
    }

    @Transactional
    public FriendRequestResponse sendRequest(ObjectId requesterId, SendFriendRequest request) {
        String usernameKey = Keys.normalize(request.username());
        UserDocument addressee = userRepository.findByUsernameKey(usernameKey)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found."));
        if (addressee.id.equals(requesterId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "You cannot add yourself as a friend.");
        }

        Instant now = Instant.now();
        String pairKey = pairKey(requesterId, addressee.id);
        FriendshipDocument friendship = friendshipRepository.findByPairKey(pairKey).orElse(null);
        if (friendship != null && friendship.status == FriendStatus.ACCEPTED) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "You are already friends.");
        }
        if (friendship != null && friendship.status == FriendStatus.PENDING) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Friend request is already pending.");
        }
        if (friendship != null && friendship.status == FriendStatus.BLOCKED) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Friend request is not allowed.");
        }
        if (friendship == null) {
            friendship = new FriendshipDocument();
            friendship.pairKey = pairKey;
        }
        friendship.requesterId = requesterId;
        friendship.addresseeId = addressee.id;
        friendship.status = FriendStatus.PENDING;
        friendship.requestedAt = now;
        friendship.respondedAt = null;
        friendship.updatedAt = now;
        friendship = friendshipRepository.save(friendship);
        return new FriendRequestResponse(friendship.id.toHexString(), userResponse(addressee), FriendRequestDirection.OUTGOING, friendship.requestedAt);
    }

    @Transactional
    public FriendResponse accept(ObjectId userId, String requestIdValue) {
        FriendshipDocument friendship = requireRequest(requestIdValue);
        if (!friendship.addresseeId.equals(userId)) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Only the request recipient can accept it.");
        }
        Instant now = Instant.now();
        friendship.status = FriendStatus.ACCEPTED;
        friendship.respondedAt = now;
        friendship.updatedAt = now;
        friendship = friendshipRepository.save(friendship);
        UserDocument friend = requireUser(friendship.requesterId);
        return new FriendResponse(friendship.id.toHexString(), userResponse(friend), friendship.respondedAt);
    }

    @Transactional
    public void reject(ObjectId userId, String requestIdValue) {
        FriendshipDocument friendship = requireRequest(requestIdValue);
        if (!friendship.addresseeId.equals(userId)) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Only the request recipient can reject it.");
        }
        friendship.status = FriendStatus.REJECTED;
        friendship.respondedAt = Instant.now();
        friendship.updatedAt = friendship.respondedAt;
        friendshipRepository.save(friendship);
    }

    @Transactional
    public void remove(ObjectId userId, String friendUserIdValue) {
        ObjectId friendUserId = ObjectIds.parse(friendUserIdValue);
        String pairKey = pairKey(userId, friendUserId);
        FriendshipDocument friendship = friendshipRepository.findByPairKey(pairKey)
                .filter(existing -> existing.status == FriendStatus.ACCEPTED)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Friendship not found."));
        friendshipRepository.delete(friendship);
    }

    public void requireFriends(ObjectId left, ObjectId right) {
        if (!areFriends(left, right)) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Direct messages are only available between friends.");
        }
    }

    public boolean areFriends(ObjectId left, ObjectId right) {
        return friendshipRepository.findByPairKey(pairKey(left, right))
                .filter(friendship -> friendship.status == FriendStatus.ACCEPTED)
                .isPresent();
    }

    public static String pairKey(ObjectId left, ObjectId right) {
        String leftValue = left.toHexString();
        String rightValue = right.toHexString();
        return leftValue.compareTo(rightValue) <= 0 ? leftValue + ":" + rightValue : rightValue + ":" + leftValue;
    }

    private FriendshipDocument requireRequest(String requestIdValue) {
        return friendshipRepository.findById(ObjectIds.parse(requestIdValue))
                .filter(friendship -> friendship.status == FriendStatus.PENDING)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Friend request not found."));
    }

    private UserDocument requireUser(ObjectId userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found."));
    }

    private FriendUserResponse userResponse(UserDocument user) {
        return new FriendUserResponse(
                user.id.toHexString(),
                user.username,
                user.displayName,
                user.avatarUrl,
                user.customStatus,
                user.lastSeenAt,
                presenceLookupService.status(user.id)
        );
    }

    private ObjectId otherUserId(FriendshipDocument friendship, ObjectId userId) {
        return friendship.requesterId.equals(userId) ? friendship.addresseeId : friendship.requesterId;
    }

}
