package dev.osunolimits.api;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import dev.osunolimits.common.APIRequest;
import dev.osunolimits.main.App;
import dev.osunolimits.models.Group;
import dev.osunolimits.models.UserInfoObject;
import dev.osunolimits.modules.utils.ShiinaSupporterBadge;
import dev.osunolimits.utils.osu.PermissionHelper;
import lombok.Data;
import okhttp3.Request;
import okhttp3.Response;

public class LeaderboardQuery extends APIQueryGson {

    @Data
    public class LeaderboardResponse {
        private String status;
        private LeaderboardItem[] leaderboard;
    }

    @Data
    public class LeaderboardItem {
        @SerializedName("player_id")
        private int playerId;

        @SerializedName("name")
        private String name;

        @SerializedName("country")
        private String country;

        @SerializedName("tscore")
        private long totalScore;

        @SerializedName("rscore")
        private long rankedScore;

        @SerializedName("pp")
        private int pp;

        @SerializedName("plays")
        private int plays;

        @SerializedName("playtime")
        private int playtime;

        @SerializedName("acc")
        private double accuracy;

        @SerializedName("max_combo")
        private int maxCombo;

        @SerializedName("xh_count")
        private int xhCount;

        @SerializedName("x_count")
        private int xCount;

        @SerializedName("sh_count")
        private int shCount;

        @SerializedName("s_count")
        private int sCount;

        @SerializedName("a_count")
        private int aCount;

        @SerializedName("clan_id")
        private Integer clanId;

        @SerializedName("clan_name")
        private String clanName;

        @SerializedName("clan_tag")
        private String clanTag;

        private boolean supporter = false;
        private List<Group> groups;
    }
    
    public LeaderboardQuery() {
        super("leaderboard", 5, 30);
    }

    public LeaderboardResponse getLeaderboard(String sort, int mode, int limit, int offset, Optional<String> country) {
        String url = "/v1/get_leaderboard";
        url += getParameter() + "sort=" + sort;
        url += getParameter() + "mode=" + mode;
        url += getParameter() + "limit=" + limit;
        url += getParameter() + "offset=" + offset;

        if (country.isPresent()) {
            url += getParameter() + "country=" + country.get();
        }

        Request request = APIRequest.build(url);

        try {
            Response response = client.newCall(request).execute();
            JsonElement element = JsonParser.parseString(response.body().string());
            LeaderboardResponse leaderboardResponse = gson.fromJson(element, LeaderboardResponse.class);
            for (LeaderboardItem item : leaderboardResponse.getLeaderboard()) {
                UserInfoObject userInfo = new UserInfoObject(item.getPlayerId());
                if (userInfo != null) {
                    item.setGroups(userInfo.getGroups());
                }
                item.setAccuracy((double) Math.round(item.getAccuracy() * 100) / 100);
                if (PermissionHelper.hasPrivileges(userInfo.priv, PermissionHelper.Privileges.SUPPORTER)) {
                    userInfo.groups.add(ShiinaSupporterBadge.getInstance().getGroup());
                    item.setSupporter(true);
                }

            }
            return leaderboardResponse;

        } catch (Exception e) {
            App.log.error("Failed to get Leaderboard", e);
            return null;
        }
    }
}
