package dev.osunolimits.routes.api.get.auth;

import java.sql.ResultSet;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.plugins.events.clans.OnUserDenyClanEvent;
import dev.osunolimits.plugins.events.clans.OnUserGetKickedClanEvent;
import dev.osunolimits.plugins.events.clans.OnUserJoinClanEvent;
import dev.osunolimits.plugins.events.clans.OnUserUnDenyClanEvent;
import dev.osunolimits.utils.Validation;
import spark.Request;
import spark.Response;

public class HandleClanAction extends Shiina {

    private final String clanPermQuery = "SELECT `clan_priv`, `clan_id` FROM `users` WHERE `id` = ?";
    private final String checkClanDeny = "SELECT * FROM `sh_clan_denied` WHERE `userid` = ? AND `clanid` = ?";
    private final String checkClanPending = "SELECT * FROM `sh_clan_pending` WHERE `userid` = ? AND `clanid` = ?";
    private final String insertClanDeny = "INSERT INTO `sh_clan_denied` (`userid`, `clanid`, `deny_time`) VALUES (?, ?, ?)";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (shiina.user == null) {
            return notFound(res, shiina);
        }

        String action = req.queryParams("action");

        Integer userid = null;
        if (req.queryParams("userid") != null && Validation.isNumeric(req.queryParams("userid"))) {
            userid = Integer.parseInt(req.queryParams("userid"));
        }

        // Still parsed for backwards compatibility, but never trusted.
        Integer clanid = null;
        if (req.queryParams("clanid") != null && Validation.isNumeric(req.queryParams("clanid"))) {
            clanid = Integer.parseInt(req.queryParams("clanid"));
        }

        if (action == null || userid == null) {
            return notFound(res, shiina);
        }

        ResultSet clanPermRS = shiina.mysql.Query(clanPermQuery, shiina.user.id);
        if (!clanPermRS.next()) {
            return notFound(res, shiina);
        }

        if (!"3".equals(clanPermRS.getString("clan_priv"))) {
            return raw(res, shiina, "not_leader");
        }

        // Always use the authenticated leader's clan.
        int leaderClanId = clanPermRS.getInt("clan_id");

        if (leaderClanId <= 0) {
            return raw(res, shiina, "invalid_clan");
        }

        switch (action.toUpperCase()) {

            case "UNDENY": {
                ResultSet checkClanDenyRS = shiina.mysql.Query(checkClanDeny, userid, leaderClanId);

                if (checkClanDenyRS.next()) {
                    shiina.mysql.Exec(
                            "DELETE FROM `sh_clan_denied` WHERE `userid` = ? AND `clanid` = ?",
                            userid,
                            leaderClanId
                    );

                    new OnUserUnDenyClanEvent(
                            leaderClanId,
                            userid,
                            shiina.user.id
                    ).callListeners();
                }
                break;
            }

            case "DENY": {
                ResultSet checkClanPendingRS = shiina.mysql.Query(
                        checkClanPending,
                        userid,
                        leaderClanId
                );

                if (checkClanPendingRS.next()) {
                    shiina.mysql.Exec(
                            "DELETE FROM `sh_clan_pending` WHERE `userid` = ? AND `clanid` = ?",
                            userid,
                            leaderClanId
                    );

                    shiina.mysql.Exec(
                            insertClanDeny,
                            userid,
                            leaderClanId,
                            System.currentTimeMillis() / 1000
                    );

                    new OnUserDenyClanEvent(
                            leaderClanId,
                            userid,
                            shiina.user.id
                    ).callListeners();
                }
                break;
            }

            case "ACCEPT": {
                ResultSet checkClanPendingRS = shiina.mysql.Query(
                        checkClanPending,
                        userid,
                        leaderClanId
                );

                if (!checkClanPendingRS.next()) {
                    return raw(res, shiina, "invalid_request");
                }

                ResultSet targetRS = shiina.mysql.Query(
                        "SELECT `clan_id` FROM `users` WHERE `id` = ?",
                        userid
                );

                if (!targetRS.next()) {
                    return raw(res, shiina, "user_not_found");
                }

                if (targetRS.getInt("clan_id") != 0) {
                    return raw(res, shiina, "already_in_clan");
                }

                shiina.mysql.Exec(
                        "DELETE FROM `sh_clan_pending` WHERE `userid` = ? AND `clanid` = ?",
                        userid,
                        leaderClanId
                );

                shiina.mysql.Exec(
                        "UPDATE `users` SET `clan_id` = ?, `clan_priv` = 1 WHERE `id` = ?",
                        leaderClanId,
                        userid
                );

                new OnUserJoinClanEvent(
                        leaderClanId,
                        userid,
                        shiina.user.id
                ).callListeners();

                break;
            }

            case "KICK": {
                ResultSet targetRS = shiina.mysql.Query(
                        "SELECT `clan_id` FROM `users` WHERE `id` = ?",
                        userid
                );

                if (!targetRS.next()) {
                    return raw(res, shiina, "user_not_found");
                }

                if (targetRS.getInt("clan_id") != leaderClanId) {
                    return raw(res, shiina, "user_not_in_clan");
                }

                shiina.mysql.Exec(
                        "UPDATE `users` SET `clan_id` = 0, `clan_priv` = 0 WHERE `id` = ? AND `clan_id` = ?",
                        userid,
                        leaderClanId
                );

                shiina.mysql.Exec(
                        insertClanDeny,
                        userid,
                        leaderClanId,
                        System.currentTimeMillis() / 1000
                );

                new OnUserGetKickedClanEvent(
                        leaderClanId,
                        userid,
                        shiina.user.id
                ).callListeners();

                break;
            }

            default:
                return raw(res, shiina, "invalid action");
        }

        return raw(res, shiina, "success");
    }
}