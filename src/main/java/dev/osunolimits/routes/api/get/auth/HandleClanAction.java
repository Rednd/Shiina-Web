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

        if(shiina.user == null) {
            return notFound(res, shiina);
        }

        String action = null;
        if (req.queryParams("action") != null) {
            action = req.queryParams("action");
        }

        Integer userid = null;
        if (req.queryParams("userid") != null && Validation.isNumeric(req.queryParams("userid"))) {
            userid = Integer.parseInt(req.queryParams("userid"));
        }

        Integer clanid = null;
        if (req.queryParams("clanid") != null && Validation.isNumeric(req.queryParams("clanid"))) {
            clanid = Integer.parseInt(req.queryParams("clanid"));
        }

        ResultSet clanPermRS = shiina.mysql.Query(clanPermQuery, shiina.user.id);
        if(!clanPermRS.next()) {
            return notFound(res, shiina);
        }

        if(!clanPermRS.getString("clan_priv").equals("3")) {
            return raw(res, shiina, "not_leader");
        }

        if(action == null || userid == null || clanid == null) {
            return notFound(res, shiina);
        }
        

        switch (action.toUpperCase()) {
            case "UNDENY":
                ResultSet checkClanDenyRS = shiina.mysql.Query(checkClanDeny, userid, clanid);
                if(checkClanDenyRS.next()) {
                    shiina.mysql.Exec("DELETE FROM `sh_clan_denied` WHERE `userid` = ? AND `clanid` = ?", userid, clanid);
                    new OnUserUnDenyClanEvent(clanid, userid, shiina.user.id).callListeners();;
                }
                break;
            case "DENY":
                ResultSet checkClanPendingRS = shiina.mysql.Query(checkClanPending, userid, clanid);
                if(checkClanPendingRS.next()) {
                    shiina.mysql.Exec("DELETE FROM `sh_clan_pending` WHERE `userid` = ? AND `clanid` = ?", userid, clanid);
                    shiina.mysql.Exec(insertClanDeny, userid, clanid, System.currentTimeMillis() / 1000);
                    new OnUserDenyClanEvent(clanid, userid, shiina.user.id).callListeners();
                }
                break;
            case "ACCEPT":
                ResultSet checkClanPendingRS2 = shiina.mysql.Query(checkClanPending, userid, clanid);
                if(checkClanPendingRS2.next()) {
                    shiina.mysql.Exec("DELETE FROM `sh_clan_pending` WHERE `userid` = ?", userid);
                    shiina.mysql.Exec("UPDATE `users` SET `clan_id` = ?, `clan_priv` = 1 WHERE `id` = ?", clanid, userid);
                    new OnUserJoinClanEvent(clanid, userid, shiina.user.id).callListeners();
                }
                break;
            case "KICK":
                shiina.mysql.Exec("UPDATE `users` SET `clan_id` = 0, `clan_priv` = 0 WHERE `id` = ?", userid);
                shiina.mysql.Exec(insertClanDeny, userid, clanid, System.currentTimeMillis() / 1000);
                new OnUserGetKickedClanEvent(clanid, userid, shiina.user.id).callListeners();
                break;
            default:
                return raw(res, shiina, "invalid action");
        }
        return raw(res, shiina, "success");
    }
}
