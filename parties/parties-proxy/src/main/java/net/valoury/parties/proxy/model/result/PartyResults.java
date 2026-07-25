package net.valoury.parties.proxy.model.result;

import net.valoury.parties.proxy.model.PartyResult;
import org.jspecify.annotations.NonNull;

public final class PartyResults {
    private PartyResults() {
    }

    private static @NonNull IllegalStateException unexpected(
            @NonNull String operation,
            @NonNull PartyResult result
    ) {
        return new IllegalStateException("Unexpected result for " + operation + ": " + result);
    }

    public sealed interface Create {
        static @NonNull Create from(@NonNull PartyResult result) {
            return switch (result) {
                case PARTY_CREATED -> new Created();
                case ALREADY_IN_PARTY -> new AlreadyInParty();
                default -> throw unexpected("create party", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Created ignored -> PartyResult.PARTY_CREATED;
                case AlreadyInParty ignored -> PartyResult.ALREADY_IN_PARTY;
            };
        }

        record Created() implements Create {
        }

        record AlreadyInParty() implements Create {
        }
    }

    public sealed interface Disband {
        static @NonNull Disband from(@NonNull PartyResult result) {
            return switch (result) {
                case PARTY_DISBANDED -> new Disbanded();
                case DISBAND_CONFIRMATION_REQUIRED -> new ConfirmationRequired();
                case NOT_IN_PARTY -> new NotInParty();
                case NOT_LEADER -> new NotLeader();
                case NO_CONFIRMATION_PENDING -> new NoConfirmationPending();
                case PARTY_NOT_FOUND -> new PartyNotFound();
                default -> throw unexpected("disband party", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Disbanded ignored -> PartyResult.PARTY_DISBANDED;
                case ConfirmationRequired ignored -> PartyResult.DISBAND_CONFIRMATION_REQUIRED;
                case NotInParty ignored -> PartyResult.NOT_IN_PARTY;
                case NotLeader ignored -> PartyResult.NOT_LEADER;
                case NoConfirmationPending ignored -> PartyResult.NO_CONFIRMATION_PENDING;
                case PartyNotFound ignored -> PartyResult.PARTY_NOT_FOUND;
            };
        }

        record Disbanded() implements Disband {
        }

        record ConfirmationRequired() implements Disband {
        }

        record NotInParty() implements Disband {
        }

        record NotLeader() implements Disband {
        }

        record NoConfirmationPending() implements Disband {
        }

        record PartyNotFound() implements Disband {
        }
    }

    public sealed interface SendInvitation {
        static @NonNull SendInvitation from(@NonNull PartyResult result) {
            return switch (result) {
                case INVITATION_SENT -> new Sent();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                case CANNOT_INVITE_SELF -> new CannotInviteSelf();
                case NOT_IN_PARTY -> new NotInParty();
                case NOT_LEADER -> new NotLeader();
                case TARGET_ALREADY_IN_PARTY -> new TargetAlreadyInParty();
                case PARTY_FULL -> new PartyFull();
                case INVITATION_COOLDOWN_ACTIVE -> new CooldownActive();
                case SENDER_INVITATION_LIMIT_REACHED -> new SenderLimitReached();
                case RECEIVER_INVITATION_LIMIT_REACHED -> new ReceiverLimitReached();
                case ALREADY_INVITED -> new AlreadyInvited();
                case INVITES_DISABLED -> new InvitesDisabled();
                case INVITES_FRIENDS_ONLY -> new InvitesFriendsOnly();
                case PARTY_NOT_FOUND -> new PartyNotFound();
                default -> throw unexpected("send party invitation", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Sent ignored -> PartyResult.INVITATION_SENT;
                case PlayerNotFound ignored -> PartyResult.PLAYER_NOT_FOUND;
                case CannotInviteSelf ignored -> PartyResult.CANNOT_INVITE_SELF;
                case NotInParty ignored -> PartyResult.NOT_IN_PARTY;
                case NotLeader ignored -> PartyResult.NOT_LEADER;
                case TargetAlreadyInParty ignored -> PartyResult.TARGET_ALREADY_IN_PARTY;
                case PartyFull ignored -> PartyResult.PARTY_FULL;
                case CooldownActive ignored -> PartyResult.INVITATION_COOLDOWN_ACTIVE;
                case SenderLimitReached ignored -> PartyResult.SENDER_INVITATION_LIMIT_REACHED;
                case ReceiverLimitReached ignored -> PartyResult.RECEIVER_INVITATION_LIMIT_REACHED;
                case AlreadyInvited ignored -> PartyResult.ALREADY_INVITED;
                case InvitesDisabled ignored -> PartyResult.INVITES_DISABLED;
                case InvitesFriendsOnly ignored -> PartyResult.INVITES_FRIENDS_ONLY;
                case PartyNotFound ignored -> PartyResult.PARTY_NOT_FOUND;
            };
        }

        record Sent() implements SendInvitation {
        }

        record PlayerNotFound() implements SendInvitation {
        }

        record CannotInviteSelf() implements SendInvitation {
        }

        record NotInParty() implements SendInvitation {
        }

        record NotLeader() implements SendInvitation {
        }

        record TargetAlreadyInParty() implements SendInvitation {
        }

        record PartyFull() implements SendInvitation {
        }

        record CooldownActive() implements SendInvitation {
        }

        record SenderLimitReached() implements SendInvitation {
        }

        record ReceiverLimitReached() implements SendInvitation {
        }

        record AlreadyInvited() implements SendInvitation {
        }

        record InvitesDisabled() implements SendInvitation {
        }

        record InvitesFriendsOnly() implements SendInvitation {
        }

        record PartyNotFound() implements SendInvitation {
        }
    }

    public sealed interface AcceptInvitation {
        static @NonNull AcceptInvitation from(@NonNull PartyResult result) {
            return switch (result) {
                case JOINED_PARTY -> new Joined();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                case ALREADY_IN_PARTY -> new AlreadyInParty();
                case NO_INVITATION_FOUND -> new NoInvitationFound();
                case PARTY_FULL -> new PartyFull();
                default -> throw unexpected("accept party invitation", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Joined ignored -> PartyResult.JOINED_PARTY;
                case PlayerNotFound ignored -> PartyResult.PLAYER_NOT_FOUND;
                case AlreadyInParty ignored -> PartyResult.ALREADY_IN_PARTY;
                case NoInvitationFound ignored -> PartyResult.NO_INVITATION_FOUND;
                case PartyFull ignored -> PartyResult.PARTY_FULL;
            };
        }

        record Joined() implements AcceptInvitation {
        }

        record PlayerNotFound() implements AcceptInvitation {
        }

        record AlreadyInParty() implements AcceptInvitation {
        }

        record NoInvitationFound() implements AcceptInvitation {
        }

        record PartyFull() implements AcceptInvitation {
        }
    }

    public sealed interface RejectInvitation {
        static @NonNull RejectInvitation from(@NonNull PartyResult result) {
            return switch (result) {
                case INVITATION_REJECTED -> new Rejected();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                case NO_INVITATION_FOUND -> new NoInvitationFound();
                default -> throw unexpected("reject party invitation", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Rejected ignored -> PartyResult.INVITATION_REJECTED;
                case PlayerNotFound ignored -> PartyResult.PLAYER_NOT_FOUND;
                case NoInvitationFound ignored -> PartyResult.NO_INVITATION_FOUND;
            };
        }

        record Rejected() implements RejectInvitation {
        }

        record PlayerNotFound() implements RejectInvitation {
        }

        record NoInvitationFound() implements RejectInvitation {
        }
    }

    public sealed interface RevokeInvitation {
        static @NonNull RevokeInvitation from(@NonNull PartyResult result) {
            return switch (result) {
                case INVITATION_REVOKED -> new Revoked();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                case NOT_IN_PARTY -> new NotInParty();
                case NOT_LEADER -> new NotLeader();
                case NO_INVITATION_FOUND -> new NoInvitationFound();
                default -> throw unexpected("revoke party invitation", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Revoked ignored -> PartyResult.INVITATION_REVOKED;
                case PlayerNotFound ignored -> PartyResult.PLAYER_NOT_FOUND;
                case NotInParty ignored -> PartyResult.NOT_IN_PARTY;
                case NotLeader ignored -> PartyResult.NOT_LEADER;
                case NoInvitationFound ignored -> PartyResult.NO_INVITATION_FOUND;
            };
        }

        record Revoked() implements RevokeInvitation {
        }

        record PlayerNotFound() implements RevokeInvitation {
        }

        record NotInParty() implements RevokeInvitation {
        }

        record NotLeader() implements RevokeInvitation {
        }

        record NoInvitationFound() implements RevokeInvitation {
        }
    }

    public sealed interface Leave {
        static @NonNull Leave from(@NonNull PartyResult result) {
            return switch (result) {
                case LEFT_PARTY -> new Left();
                case LEFT_PARTY_DISBANDED -> new LeftAndDisbanded();
                case NOT_IN_PARTY -> new NotInParty();
                case PLAYER_NOT_IN_PARTY -> new PlayerNotInParty();
                case PARTY_NOT_FOUND -> new PartyNotFound();
                default -> throw unexpected("leave party", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Left ignored -> PartyResult.LEFT_PARTY;
                case LeftAndDisbanded ignored -> PartyResult.LEFT_PARTY_DISBANDED;
                case NotInParty ignored -> PartyResult.NOT_IN_PARTY;
                case PlayerNotInParty ignored -> PartyResult.PLAYER_NOT_IN_PARTY;
                case PartyNotFound ignored -> PartyResult.PARTY_NOT_FOUND;
            };
        }

        record Left() implements Leave {
        }

        record LeftAndDisbanded() implements Leave {
        }

        record NotInParty() implements Leave {
        }

        record PlayerNotInParty() implements Leave {
        }

        record PartyNotFound() implements Leave {
        }
    }

    public sealed interface KickMember {
        static @NonNull KickMember from(@NonNull PartyResult result) {
            return switch (result) {
                case MEMBER_KICKED -> new Kicked();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                case CANNOT_KICK_SELF -> new CannotKickSelf();
                case NOT_IN_PARTY -> new NotInParty();
                case NOT_LEADER -> new NotLeader();
                case PLAYER_NOT_IN_PARTY -> new PlayerNotInParty();
                case PARTY_NOT_FOUND -> new PartyNotFound();
                default -> throw unexpected("kick party member", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Kicked ignored -> PartyResult.MEMBER_KICKED;
                case PlayerNotFound ignored -> PartyResult.PLAYER_NOT_FOUND;
                case CannotKickSelf ignored -> PartyResult.CANNOT_KICK_SELF;
                case NotInParty ignored -> PartyResult.NOT_IN_PARTY;
                case NotLeader ignored -> PartyResult.NOT_LEADER;
                case PlayerNotInParty ignored -> PartyResult.PLAYER_NOT_IN_PARTY;
                case PartyNotFound ignored -> PartyResult.PARTY_NOT_FOUND;
            };
        }

        record Kicked() implements KickMember {
        }

        record PlayerNotFound() implements KickMember {
        }

        record CannotKickSelf() implements KickMember {
        }

        record NotInParty() implements KickMember {
        }

        record NotLeader() implements KickMember {
        }

        record PlayerNotInParty() implements KickMember {
        }

        record PartyNotFound() implements KickMember {
        }
    }

    public sealed interface SendChat {
        static @NonNull SendChat from(@NonNull PartyResult result) {
            return switch (result) {
                case CHAT_SENT -> new Sent();
                case MESSAGE_TOO_LONG -> new MessageTooLong();
                case NOT_IN_PARTY -> new NotInParty();
                case CHAT_DISABLED -> new ChatDisabled();
                default -> throw unexpected("send party chat", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Sent ignored -> PartyResult.CHAT_SENT;
                case MessageTooLong ignored -> PartyResult.MESSAGE_TOO_LONG;
                case NotInParty ignored -> PartyResult.NOT_IN_PARTY;
                case ChatDisabled ignored -> PartyResult.CHAT_DISABLED;
            };
        }

        record Sent() implements SendChat {
        }

        record MessageTooLong() implements SendChat {
        }

        record NotInParty() implements SendChat {
        }

        record ChatDisabled() implements SendChat {
        }
    }

    public sealed interface TransferLeadership {
        static @NonNull TransferLeadership from(@NonNull PartyResult result) {
            return switch (result) {
                case LEADERSHIP_TRANSFERRED -> new Transferred();
                case TRANSFER_CONFIRMATION_REQUIRED -> new ConfirmationRequired();
                case NO_CONFIRMATION_PENDING -> new NoConfirmationPending();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                case CANNOT_TRANSFER_TO_SELF -> new CannotTransferToSelf();
                case NOT_IN_PARTY -> new NotInParty();
                case NOT_LEADER -> new NotLeader();
                case PLAYER_NOT_IN_PARTY -> new PlayerNotInParty();
                case PARTY_NOT_FOUND -> new PartyNotFound();
                default -> throw unexpected("transfer party leadership", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Transferred ignored -> PartyResult.LEADERSHIP_TRANSFERRED;
                case ConfirmationRequired ignored -> PartyResult.TRANSFER_CONFIRMATION_REQUIRED;
                case NoConfirmationPending ignored -> PartyResult.NO_CONFIRMATION_PENDING;
                case PlayerNotFound ignored -> PartyResult.PLAYER_NOT_FOUND;
                case CannotTransferToSelf ignored -> PartyResult.CANNOT_TRANSFER_TO_SELF;
                case NotInParty ignored -> PartyResult.NOT_IN_PARTY;
                case NotLeader ignored -> PartyResult.NOT_LEADER;
                case PlayerNotInParty ignored -> PartyResult.PLAYER_NOT_IN_PARTY;
                case PartyNotFound ignored -> PartyResult.PARTY_NOT_FOUND;
            };
        }

        record Transferred() implements TransferLeadership {
        }

        record ConfirmationRequired() implements TransferLeadership {
        }

        record NoConfirmationPending() implements TransferLeadership {
        }

        record PlayerNotFound() implements TransferLeadership {
        }

        record CannotTransferToSelf() implements TransferLeadership {
        }

        record NotInParty() implements TransferLeadership {
        }

        record NotLeader() implements TransferLeadership {
        }

        record PlayerNotInParty() implements TransferLeadership {
        }

        record PartyNotFound() implements TransferLeadership {
        }
    }

    public sealed interface Warp {
        static @NonNull Warp from(@NonNull PartyResult result) {
            return switch (result) {
                case PARTY_WARPED -> new Warped();
                case NOT_IN_PARTY -> new NotInParty();
                case NOT_LEADER -> new NotLeader();
                case WARP_ON_COOLDOWN -> new OnCooldown();
                case PARTY_NOT_FOUND -> new PartyNotFound();
                case WARP_FAILED -> new Failed();
                default -> throw unexpected("warp party", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Warped ignored -> PartyResult.PARTY_WARPED;
                case NotInParty ignored -> PartyResult.NOT_IN_PARTY;
                case NotLeader ignored -> PartyResult.NOT_LEADER;
                case OnCooldown ignored -> PartyResult.WARP_ON_COOLDOWN;
                case PartyNotFound ignored -> PartyResult.PARTY_NOT_FOUND;
                case Failed ignored -> PartyResult.WARP_FAILED;
            };
        }

        record Warped() implements Warp {
        }

        record NotInParty() implements Warp {
        }

        record NotLeader() implements Warp {
        }

        record OnCooldown() implements Warp {
        }

        record PartyNotFound() implements Warp {
        }

        record Failed() implements Warp {
        }
    }

    public sealed interface JumpToLeader {
        static @NonNull JumpToLeader from(@NonNull PartyResult result) {
            return switch (result) {
                case JUMPED_TO_LEADER -> new Jumped();
                case NOT_IN_PARTY -> new NotInParty();
                case CANNOT_JUMP_AS_LEADER -> new CannotJumpAsLeader();
                case LEADER_NOT_ONLINE -> new LeaderNotOnline();
                case LEADER_NO_INSTANCE -> new LeaderHasNoInstance();
                case ALREADY_WITH_LEADER -> new AlreadyWithLeader();
                case WARP_FAILED -> new Failed();
                default -> throw unexpected("jump to party leader", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Jumped ignored -> PartyResult.JUMPED_TO_LEADER;
                case NotInParty ignored -> PartyResult.NOT_IN_PARTY;
                case CannotJumpAsLeader ignored -> PartyResult.CANNOT_JUMP_AS_LEADER;
                case LeaderNotOnline ignored -> PartyResult.LEADER_NOT_ONLINE;
                case LeaderHasNoInstance ignored -> PartyResult.LEADER_NO_INSTANCE;
                case AlreadyWithLeader ignored -> PartyResult.ALREADY_WITH_LEADER;
                case Failed ignored -> PartyResult.WARP_FAILED;
            };
        }

        record Jumped() implements JumpToLeader {
        }

        record NotInParty() implements JumpToLeader {
        }

        record CannotJumpAsLeader() implements JumpToLeader {
        }

        record LeaderNotOnline() implements JumpToLeader {
        }

        record LeaderHasNoInstance() implements JumpToLeader {
        }

        record AlreadyWithLeader() implements JumpToLeader {
        }

        record Failed() implements JumpToLeader {
        }
    }

    public sealed interface UpdateSetting {
        static @NonNull UpdateSetting from(@NonNull PartyResult result) {
            return switch (result) {
                case SETTING_UPDATED -> new Updated();
                case INVALID_SETTING -> new InvalidSetting();
                default -> throw unexpected("update party setting", result);
            };
        }

        default @NonNull PartyResult legacy() {
            return switch (this) {
                case Updated ignored -> PartyResult.SETTING_UPDATED;
                case InvalidSetting ignored -> PartyResult.INVALID_SETTING;
            };
        }

        record Updated() implements UpdateSetting {
        }

        record InvalidSetting() implements UpdateSetting {
        }
    }
}
