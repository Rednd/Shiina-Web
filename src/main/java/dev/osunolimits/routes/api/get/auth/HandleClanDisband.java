package dev.osunolimits.routes.api.get.auth;

import dev.osunolimits.models.Clan;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.plugins.events.clans.OnUserDisbandClanEvent;
import spark.Request;
import spark.Response;

public class HandleClanDisband extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if(shiina.user == null) {
            return notFound(res, shiina);
        }
        int clanId = 0;
        var clanCheck = shiina.mysql.Query("SELECT u.`clan_id`, c.`name`, c.`tag`, `u`.`clan_priv` FROM `users` u LEFT JOIN `clans` c ON u.`clan_id` = c.`id` WHERE u.`id` = ?", shiina.user.id);
        if (clanCheck.next()) {

            clanId = clanCheck.getInt("clan_id");
            if (clanId == 0) {
                return raw(res, shiina, "not_in_clan");
            }

            int clanPriv = clanCheck.getInt("clan_priv");
            if (clanPriv != 3) {
                return raw(res, shiina, "not_leader");
            }

            Clan clan = new Clan(
                clanCheck.getInt("clan_id"),
                clanCheck.getString("name"),
                clanCheck.getString("tag")
            );
            
            // Trigger event
            OnUserDisbandClanEvent event = new OnUserDisbandClanEvent(clan, shiina.user.id);
            event.callListeners();
        }


        shiina.mysql.Exec("UPDATE `users` SET `clan_id`=0,`clan_priv`=0 WHERE `clan_id` = ?", clanId);
        shiina.mysql.Exec("DELETE FROM `clans` WHERE `id` = ?", clanId);
        shiina.mysql.Exec("DELETE FROM `sh_clan_denied` WHERE `clanid` = ?", clanId);
        shiina.mysql.Exec("DELETE FROM `sh_clan_pending` WHERE `clanid` = ?", clanId);
        
        return redirect(res, shiina, "/clans");
    }
}
