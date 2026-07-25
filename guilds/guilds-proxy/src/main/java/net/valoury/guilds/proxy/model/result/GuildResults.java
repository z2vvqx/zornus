package net.valoury.guilds.proxy.model.result;

import net.valoury.guilds.proxy.model.GuildResult;
import org.jspecify.annotations.NonNull;

public final class GuildResults {
    private GuildResults() {
    }

    private static @NonNull IllegalStateException unexpected(
            @NonNull String operation,
            @NonNull GuildResult result
    ) {
        return new IllegalStateException("Unexpected result for " + operation + ": " + result);
    }

    public sealed interface Create {
        static @NonNull Create from(@NonNull GuildResult result) {
            return switch (result) {
                case GUILD_CREATED -> new Created();
                case ALREADY_IN_GUILD -> new AlreadyInGuild();
                case INVALID_GUILD_NAME -> new InvalidName();
                case INVALID_GUILD_TAG -> new InvalidTag();
                case NAME_ALREADY_EXISTS -> new NameAlreadyExists();
                default -> throw unexpected("create guild", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Created ignored -> GuildResult.GUILD_CREATED;
                case AlreadyInGuild ignored -> GuildResult.ALREADY_IN_GUILD;
                case InvalidName ignored -> GuildResult.INVALID_GUILD_NAME;
                case InvalidTag ignored -> GuildResult.INVALID_GUILD_TAG;
                case NameAlreadyExists ignored -> GuildResult.NAME_ALREADY_EXISTS;
            };
        }

        record Created() implements Create {
        }

        record AlreadyInGuild() implements Create {
        }

        record InvalidName() implements Create {
        }

        record InvalidTag() implements Create {
        }

        record NameAlreadyExists() implements Create {
        }
    }

    public sealed interface Disband {
        static @NonNull Disband from(@NonNull GuildResult result) {
            return switch (result) {
                case GUILD_DISBANDED -> new Disbanded();
                case DISBAND_CONFIRMATION_REQUIRED -> new ConfirmationRequired();
                case NO_CONFIRMATION_PENDING -> new NoConfirmationPending();
                case NOT_IN_GUILD -> new NotInGuild();
                case NOT_LEADER -> new NotLeader();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                default -> throw unexpected("disband guild", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Disbanded ignored -> GuildResult.GUILD_DISBANDED;
                case ConfirmationRequired ignored -> GuildResult.DISBAND_CONFIRMATION_REQUIRED;
                case NoConfirmationPending ignored -> GuildResult.NO_CONFIRMATION_PENDING;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case NotLeader ignored -> GuildResult.NOT_LEADER;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
            };
        }

        record Disbanded() implements Disband {
        }

        record ConfirmationRequired() implements Disband {
        }

        record NoConfirmationPending() implements Disband {
        }

        record NotInGuild() implements Disband {
        }

        record NotLeader() implements Disband {
        }

        record GuildNotFound() implements Disband {
        }
    }

    public sealed interface SendInvitation {
        static @NonNull SendInvitation from(@NonNull GuildResult result) {
            return switch (result) {
                case INVITATION_SENT -> new Sent();
                case NOT_IN_GUILD -> new NotInGuild();
                case NOT_LEADER -> new NotLeader();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                case CANNOT_INVITE_SELF -> new CannotInviteSelf();
                case TARGET_ALREADY_IN_GUILD -> new TargetAlreadyInGuild();
                case TARGET_IN_ANOTHER_GUILD -> new TargetInAnotherGuild();
                case GUILD_FULL -> new GuildFull();
                case ALREADY_INVITED -> new AlreadyInvited();
                case SENDER_INVITATION_LIMIT_REACHED -> new SenderLimitReached();
                case RECEIVER_INVITATION_LIMIT_REACHED -> new ReceiverLimitReached();
                case INVITATION_COOLDOWN_ACTIVE -> new CooldownActive();
                case INVITES_DISABLED -> new InvitesDisabled();
                case INVITES_FRIENDS_ONLY -> new InvitesFriendsOnly();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                default -> throw unexpected("send guild invitation", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Sent ignored -> GuildResult.INVITATION_SENT;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case NotLeader ignored -> GuildResult.NOT_LEADER;
                case PlayerNotFound ignored -> GuildResult.PLAYER_NOT_FOUND;
                case CannotInviteSelf ignored -> GuildResult.CANNOT_INVITE_SELF;
                case TargetAlreadyInGuild ignored -> GuildResult.TARGET_ALREADY_IN_GUILD;
                case TargetInAnotherGuild ignored -> GuildResult.TARGET_IN_ANOTHER_GUILD;
                case GuildFull ignored -> GuildResult.GUILD_FULL;
                case AlreadyInvited ignored -> GuildResult.ALREADY_INVITED;
                case SenderLimitReached ignored -> GuildResult.SENDER_INVITATION_LIMIT_REACHED;
                case ReceiverLimitReached ignored -> GuildResult.RECEIVER_INVITATION_LIMIT_REACHED;
                case CooldownActive ignored -> GuildResult.INVITATION_COOLDOWN_ACTIVE;
                case InvitesDisabled ignored -> GuildResult.INVITES_DISABLED;
                case InvitesFriendsOnly ignored -> GuildResult.INVITES_FRIENDS_ONLY;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
            };
        }

        record Sent() implements SendInvitation {
        }

        record NotInGuild() implements SendInvitation {
        }

        record NotLeader() implements SendInvitation {
        }

        record PlayerNotFound() implements SendInvitation {
        }

        record CannotInviteSelf() implements SendInvitation {
        }

        record TargetAlreadyInGuild() implements SendInvitation {
        }

        record TargetInAnotherGuild() implements SendInvitation {
        }

        record GuildFull() implements SendInvitation {
        }

        record AlreadyInvited() implements SendInvitation {
        }

        record SenderLimitReached() implements SendInvitation {
        }

        record ReceiverLimitReached() implements SendInvitation {
        }

        record CooldownActive() implements SendInvitation {
        }

        record InvitesDisabled() implements SendInvitation {
        }

        record InvitesFriendsOnly() implements SendInvitation {
        }

        record GuildNotFound() implements SendInvitation {
        }
    }

    public sealed interface AcceptInvitation {
        static @NonNull AcceptInvitation from(@NonNull GuildResult result) {
            return switch (result) {
                case JOINED_GUILD -> new Joined();
                case NO_INVITATION_FOUND -> new NoInvitationFound();
                case GUILD_FULL -> new GuildFull();
                case ALREADY_IN_GUILD -> new AlreadyInGuild();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                default -> throw unexpected("accept guild invitation", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Joined ignored -> GuildResult.JOINED_GUILD;
                case NoInvitationFound ignored -> GuildResult.NO_INVITATION_FOUND;
                case GuildFull ignored -> GuildResult.GUILD_FULL;
                case AlreadyInGuild ignored -> GuildResult.ALREADY_IN_GUILD;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
            };
        }

        record Joined() implements AcceptInvitation {
        }

        record NoInvitationFound() implements AcceptInvitation {
        }

        record GuildFull() implements AcceptInvitation {
        }

        record AlreadyInGuild() implements AcceptInvitation {
        }

        record GuildNotFound() implements AcceptInvitation {
        }
    }

    public sealed interface RejectInvitation {
        static @NonNull RejectInvitation from(@NonNull GuildResult result) {
            return switch (result) {
                case INVITATION_REJECTED -> new Rejected();
                case NO_INVITATION_FOUND -> new NoInvitationFound();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                default -> throw unexpected("reject guild invitation", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Rejected ignored -> GuildResult.INVITATION_REJECTED;
                case NoInvitationFound ignored -> GuildResult.NO_INVITATION_FOUND;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
            };
        }

        record Rejected() implements RejectInvitation {
        }

        record NoInvitationFound() implements RejectInvitation {
        }

        record GuildNotFound() implements RejectInvitation {
        }
    }

    public sealed interface RevokeInvitation {
        static @NonNull RevokeInvitation from(@NonNull GuildResult result) {
            return switch (result) {
                case INVITATION_REVOKED -> new Revoked();
                case NO_INVITATION_FOUND -> new NoInvitationFound();
                case NOT_IN_GUILD -> new NotInGuild();
                case NOT_LEADER -> new NotLeader();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                default -> throw unexpected("revoke guild invitation", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Revoked ignored -> GuildResult.INVITATION_REVOKED;
                case NoInvitationFound ignored -> GuildResult.NO_INVITATION_FOUND;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case NotLeader ignored -> GuildResult.NOT_LEADER;
                case PlayerNotFound ignored -> GuildResult.PLAYER_NOT_FOUND;
            };
        }

        record Revoked() implements RevokeInvitation {
        }

        record NoInvitationFound() implements RevokeInvitation {
        }

        record NotInGuild() implements RevokeInvitation {
        }

        record NotLeader() implements RevokeInvitation {
        }

        record PlayerNotFound() implements RevokeInvitation {
        }
    }

    public sealed interface Leave {
        static @NonNull Leave from(@NonNull GuildResult result) {
            return switch (result) {
                case LEFT_GUILD -> new Left();
                case LEFT_GUILD_DISBANDED -> new LeftAndDisbanded();
                case NOT_IN_GUILD -> new NotInGuild();
                case PLAYER_NOT_IN_GUILD -> new PlayerNotInGuild();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                case CANNOT_REMOVE_LEADER -> new CannotRemoveLeader();
                case NOT_LEADER -> new NotLeader();
                default -> throw unexpected("leave guild", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Left ignored -> GuildResult.LEFT_GUILD;
                case LeftAndDisbanded ignored -> GuildResult.LEFT_GUILD_DISBANDED;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case PlayerNotInGuild ignored -> GuildResult.PLAYER_NOT_IN_GUILD;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
                case CannotRemoveLeader ignored -> GuildResult.CANNOT_REMOVE_LEADER;
                case NotLeader ignored -> GuildResult.NOT_LEADER;
            };
        }

        record Left() implements Leave {
        }

        record LeftAndDisbanded() implements Leave {
        }

        record NotInGuild() implements Leave {
        }

        record PlayerNotInGuild() implements Leave {
        }

        record GuildNotFound() implements Leave {
        }

        record CannotRemoveLeader() implements Leave {
        }

        record NotLeader() implements Leave {
        }
    }

    public sealed interface KickMember {
        static @NonNull KickMember from(@NonNull GuildResult result) {
            return switch (result) {
                case MEMBER_REMOVED -> new Removed();
                case LEFT_GUILD_DISBANDED -> new GuildDisbanded();
                case NOT_IN_GUILD -> new NotInGuild();
                case NOT_LEADER -> new NotLeader();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                case PLAYER_NOT_IN_GUILD -> new PlayerNotInGuild();
                case CANNOT_REMOVE_LEADER -> new CannotRemoveLeader();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                default -> throw unexpected("kick guild member", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Removed ignored -> GuildResult.MEMBER_REMOVED;
                case GuildDisbanded ignored -> GuildResult.LEFT_GUILD_DISBANDED;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case NotLeader ignored -> GuildResult.NOT_LEADER;
                case PlayerNotFound ignored -> GuildResult.PLAYER_NOT_FOUND;
                case PlayerNotInGuild ignored -> GuildResult.PLAYER_NOT_IN_GUILD;
                case CannotRemoveLeader ignored -> GuildResult.CANNOT_REMOVE_LEADER;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
            };
        }

        record Removed() implements KickMember {
        }

        record GuildDisbanded() implements KickMember {
        }

        record NotInGuild() implements KickMember {
        }

        record NotLeader() implements KickMember {
        }

        record PlayerNotFound() implements KickMember {
        }

        record PlayerNotInGuild() implements KickMember {
        }

        record CannotRemoveLeader() implements KickMember {
        }

        record GuildNotFound() implements KickMember {
        }
    }

    public sealed interface SendChat {
        static @NonNull SendChat from(@NonNull GuildResult result) {
            return switch (result) {
                case CHAT_SENT -> new Sent();
                case NOT_IN_GUILD -> new NotInGuild();
                case CHAT_DISABLED -> new ChatDisabled();
                case MESSAGE_TOO_LONG -> new MessageTooLong();
                default -> throw unexpected("send guild chat", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Sent ignored -> GuildResult.CHAT_SENT;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case ChatDisabled ignored -> GuildResult.CHAT_DISABLED;
                case MessageTooLong ignored -> GuildResult.MESSAGE_TOO_LONG;
            };
        }

        record Sent() implements SendChat {
        }

        record NotInGuild() implements SendChat {
        }

        record ChatDisabled() implements SendChat {
        }

        record MessageTooLong() implements SendChat {
        }
    }

    public sealed interface TransferLeadership {
        static @NonNull TransferLeadership from(@NonNull GuildResult result) {
            return switch (result) {
                case LEADERSHIP_TRANSFERRED -> new Transferred();
                case TRANSFER_CONFIRMATION_REQUIRED -> new ConfirmationRequired();
                case NO_CONFIRMATION_PENDING -> new NoConfirmationPending();
                case NOT_IN_GUILD -> new NotInGuild();
                case NOT_LEADER -> new NotLeader();
                case PLAYER_NOT_FOUND -> new PlayerNotFound();
                case PLAYER_NOT_IN_GUILD -> new PlayerNotInGuild();
                case CANNOT_TRANSFER_TO_SELF -> new CannotTransferToSelf();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                default -> throw unexpected("transfer guild leadership", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Transferred ignored -> GuildResult.LEADERSHIP_TRANSFERRED;
                case ConfirmationRequired ignored -> GuildResult.TRANSFER_CONFIRMATION_REQUIRED;
                case NoConfirmationPending ignored -> GuildResult.NO_CONFIRMATION_PENDING;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case NotLeader ignored -> GuildResult.NOT_LEADER;
                case PlayerNotFound ignored -> GuildResult.PLAYER_NOT_FOUND;
                case PlayerNotInGuild ignored -> GuildResult.PLAYER_NOT_IN_GUILD;
                case CannotTransferToSelf ignored -> GuildResult.CANNOT_TRANSFER_TO_SELF;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
            };
        }

        record Transferred() implements TransferLeadership {
        }

        record ConfirmationRequired() implements TransferLeadership {
        }

        record NoConfirmationPending() implements TransferLeadership {
        }

        record NotInGuild() implements TransferLeadership {
        }

        record NotLeader() implements TransferLeadership {
        }

        record PlayerNotFound() implements TransferLeadership {
        }

        record PlayerNotInGuild() implements TransferLeadership {
        }

        record CannotTransferToSelf() implements TransferLeadership {
        }

        record GuildNotFound() implements TransferLeadership {
        }
    }

    public sealed interface Rename {
        static @NonNull Rename from(@NonNull GuildResult result) {
            return switch (result) {
                case GUILD_RENAMED -> new Renamed();
                case RENAME_CONFIRMATION_REQUIRED -> new ConfirmationRequired();
                case NO_CONFIRMATION_PENDING -> new NoConfirmationPending();
                case NOT_IN_GUILD -> new NotInGuild();
                case NOT_LEADER -> new NotLeader();
                case INVALID_GUILD_NAME -> new InvalidName();
                case NAME_ALREADY_EXISTS -> new NameAlreadyExists();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                default -> throw unexpected("rename guild", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Renamed ignored -> GuildResult.GUILD_RENAMED;
                case ConfirmationRequired ignored -> GuildResult.RENAME_CONFIRMATION_REQUIRED;
                case NoConfirmationPending ignored -> GuildResult.NO_CONFIRMATION_PENDING;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case NotLeader ignored -> GuildResult.NOT_LEADER;
                case InvalidName ignored -> GuildResult.INVALID_GUILD_NAME;
                case NameAlreadyExists ignored -> GuildResult.NAME_ALREADY_EXISTS;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
            };
        }

        record Renamed() implements Rename {
        }

        record ConfirmationRequired() implements Rename {
        }

        record NoConfirmationPending() implements Rename {
        }

        record NotInGuild() implements Rename {
        }

        record NotLeader() implements Rename {
        }

        record InvalidName() implements Rename {
        }

        record NameAlreadyExists() implements Rename {
        }

        record GuildNotFound() implements Rename {
        }
    }

    public sealed interface UpdateSetting {
        static @NonNull UpdateSetting from(@NonNull GuildResult result) {
            return switch (result) {
                case SETTING_UPDATED -> new Updated();
                case INVALID_SETTING -> new InvalidSetting();
                default -> throw unexpected("update guild setting", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Updated ignored -> GuildResult.SETTING_UPDATED;
                case InvalidSetting ignored -> GuildResult.INVALID_SETTING;
            };
        }

        record Updated() implements UpdateSetting {
        }

        record InvalidSetting() implements UpdateSetting {
        }
    }

    public sealed interface UpdateTag {
        static @NonNull UpdateTag from(@NonNull GuildResult result) {
            return switch (result) {
                case GUILD_TAG_UPDATED -> new Updated();
                case INVALID_GUILD_TAG -> new InvalidTag();
                case NOT_IN_GUILD -> new NotInGuild();
                case NOT_LEADER -> new NotLeader();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                default -> throw unexpected("update guild tag", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Updated ignored -> GuildResult.GUILD_TAG_UPDATED;
                case InvalidTag ignored -> GuildResult.INVALID_GUILD_TAG;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case NotLeader ignored -> GuildResult.NOT_LEADER;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
            };
        }

        record Updated() implements UpdateTag {
        }

        record InvalidTag() implements UpdateTag {
        }

        record NotInGuild() implements UpdateTag {
        }

        record NotLeader() implements UpdateTag {
        }

        record GuildNotFound() implements UpdateTag {
        }
    }

    public sealed interface UpdateColor {
        static @NonNull UpdateColor from(@NonNull GuildResult result) {
            return switch (result) {
                case GUILD_COLOR_UPDATED -> new Updated();
                case INVALID_GUILD_COLOR -> new InvalidColor();
                case NOT_IN_GUILD -> new NotInGuild();
                case NOT_LEADER -> new NotLeader();
                case GUILD_NOT_FOUND -> new GuildNotFound();
                default -> throw unexpected("update guild color", result);
            };
        }

        default @NonNull GuildResult legacy() {
            return switch (this) {
                case Updated ignored -> GuildResult.GUILD_COLOR_UPDATED;
                case InvalidColor ignored -> GuildResult.INVALID_GUILD_COLOR;
                case NotInGuild ignored -> GuildResult.NOT_IN_GUILD;
                case NotLeader ignored -> GuildResult.NOT_LEADER;
                case GuildNotFound ignored -> GuildResult.GUILD_NOT_FOUND;
            };
        }

        record Updated() implements UpdateColor {
        }

        record InvalidColor() implements UpdateColor {
        }

        record NotInGuild() implements UpdateColor {
        }

        record NotLeader() implements UpdateColor {
        }

        record GuildNotFound() implements UpdateColor {
        }
    }
}
