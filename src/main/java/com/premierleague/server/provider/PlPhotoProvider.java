package com.premierleague.server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.premierleague.server.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlPhotoProvider {

    private static final String SEARCH_URL =
            "https://footballapi.pulselive.com/search/PremierLeague"
                    + "?terms=%s&type=player&size=3&start=0&fullObjectResponse=true";

    private static final String LEGACY_PHOTO_URL_TMPL =
            "https://resources.premierleague.com/premierleague/photos/players/250x250/%s.png";

    private static final String CURRENT_PHOTO_URL_TMPL =
            "https://resources.premierleague.com/premierleague25/photos/players/110x140/%s.png";

    private static final String CURRENT_SMALL_PHOTO_URL_TMPL =
            "https://resources.premierleague.com/premierleague25/photos/players/40x40/%s.png";

    private static final Map<String, String> IMAGE_HEADERS = Map.of(
            "Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            "Referer", "https://www.premierleague.com/",
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    );

    private static final Map<String, String> SEARCH_ALIASES = Map.ofEntries(
            Map.entry("Gabriel", "Gabriel Magalhaes"),
            Map.entry("Gabriel Magalhães", "Gabriel Magalhaes"),
            Map.entry("Sávio", "Savinho"),
            Map.entry("Savio", "Savinho"),
            Map.entry("Nico O’Reilly", "Nico O'Reilly"),
            Map.entry("Nico OReilly", "Nico O'Reilly"),
            Map.entry("Chido Obi-Martin", "Chido Obi"),
            Map.entry("Ifeoluwa Ibrahim", "Ife Ibrahim")
    );

    private static final Map<String, String> CLUB_PHOTO_OVERRIDES = Map.ofEntries(
            Map.entry("Aleksei Fedorushchenko", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3202_GYGQK3bS_WEB_h4lfkes3.png?h=ad73a5fe&auto=webp&itok=tYtqvB3T"),
            Map.entry("Alexei Rojas", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3202_GYGQK3bS_WEB_h4lfkes3.png?h=ad73a5fe&auto=webp&itok=tYtqvB3T"),
            Map.entry("Brando Bailey-Joseph", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/brando_u9e2sg3l.png?h=3c8f2bed&auto=webp&itok=-TE7-FfR"),
            Map.entry("Ife Ibrahim", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3428_ujHhxO3S_WEB_kwma9mqg.png?h=ad73a5fe&auto=webp&itok=W-1S38Hs"),
            Map.entry("Ifeoluwa Ibrahim", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3428_ujHhxO3S_WEB_kwma9mqg.png?h=ad73a5fe&auto=webp&itok=W-1S38Hs"),
            Map.entry("Jack Porter", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3220_ZiknMcM7_WEB_gxfyael4.png?h=ad73a5fe&auto=webp&itok=7uleo71I"),
            Map.entry("Harrison Dudziak", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3526_q6WLqETJ_WEB_wz4z8rqm.png?h=ad73a5fe&auto=webp&itok=0GtKk44J"),
            Map.entry("Jaden Dixon", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/jaden-dixon-profile_nuackxvy.jpg?h=68d83a20&auto=webp&itok=sCtiNCUH"),
            Map.entry("Josh Nichols", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3574_gZXPQ7c9_WEB_wwznb0mc.png?h=ad73a5fe&auto=webp&itok=OpNAMF0n"),
            Map.entry("Khari Ranson", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3916_GrU0skHz_WEB_gfumvqz5.png?h=ad73a5fe&auto=webp&itok=JzVw2A3N"),
            Map.entry("Marli Salmon", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/salmon_fwqwbleq.png?h=6dff888f&auto=webp&itok=o5HVbfm5"),
            Map.entry("Ceadach O'Neill", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3760_t05WektC_WEB_o0acbj1f.png?h=ad73a5fe&auto=webp&itok=73cjXwDl"),
            Map.entry("Ceadach O’Neill", "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3760_t05WektC_WEB_o0acbj1f.png?h=ad73a5fe&auto=webp&itok=73cjXwDl"),
            Map.entry("Landon Emenalo", "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/Academy/Stock%202025-26/U21/Emenalo_U21_IW2_1525"),
            Map.entry("Jesse Derry", "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/Academy/Jesse%20Derry%20signs/CL2_1969_erbIPMgF"),
            Map.entry("Max Merrick", "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/restricted/2026/Merrick%20contract/Merrick_contract_2026_CL1_4646_BLpGCZvL"),
            Map.entry("Ollie Harrison", "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/news/2025/09/23/Ollie_Harrison_Chelsea_Under-19s_UEFA_Youth_League"),
            Map.entry("Reggie Walsh", "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/news/2025/10/24/Walsh_contract_2025_CL1_6693_aBmqyk9Y"),
            Map.entry("Ryan Kavuma-McQueen", "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/news/2025/09/23/Ryan_Kavuma-McQueen_Chelsea"),
            Map.entry("Shumaira Mheuka", "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/news/2024/10/23/Mheuka_U21_CL1_9104_qPPw1iVP"),
            Map.entry("Charlie Holland", "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/Academy/Under-18%2025-26%20season/GettyImages-2229427989"),
            Map.entry("Kaden Braithwaite", "https://www.mancity.com/meta/media/upsc25hd/braithwaite.jpg?width=1620"),
            Map.entry("Kian Noble", "https://www.mancity.com/meta/media/k4nbqjgb/kian-noble-celebration.jpg?width=976"),
            Map.entry("Floyd Samba", "https://www.mancity.com/meta/media/bhunvole/floyd-samba.jpg?width=1620"),
            Map.entry("Ryan McAidoo", "https://www.mancity.com/meta/media/ug1b54yf/ryan-mcaidoo.jpg"),
            Map.entry("Charlie Gray", "https://www.mancity.com/meta/media/y2rirjfx/gray.jpg?width=1620"),
            Map.entry("Tyrone Samba", "https://www.mancity.com/meta/media/nwdh3t34/tyrone-samba-yl.jpg"),
            Map.entry("Bendito Mantato", "https://assets.manutd.com/AssetPicker/images/0/0/22/246/1504959/Bendito_Mantato1759498479811_large.jpg"),
            Map.entry("Godwill Kukonki", "https://assets.manutd.com/AssetPicker/images/0/0/22/246/1504970/Godwill_Kukonki1759837274632_large.jpg"),
            Map.entry("Jack Moorhouse", "https://assets.manutd.com/AssetPicker/images/0/0/23/45/1518917/GettyImages_22329200311762878413977_large.jpg"),
            Map.entry("Jim Thwaites", "https://assets.manutd.com/AssetPicker/images/0/0/22/246/1504981/Jim_Thwaites1759505599926_large.jpg"),
            Map.entry("Shea Lacey", "https://assets.manutd.com/AssetPicker/images/0/0/22/246/1504994/Shea_Lacey1759923765054_large.jpg"),
            Map.entry("Jayden Danns", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-02/Jayden-Danns-030225_99619fa3a9b38a4fa8ba04a5d10d62eb.jpg?itok=c39cOChQ"),
            Map.entry("Kornel Misciur", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/kornel-misciur-2025-26-headshot_98cea4014b973932ce225add39eefc78.jpg?itok=7dp3wMya"),
            Map.entry("Rhys Williams", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-09/rhys-williams-2025-26-v2_8cbdcb5e65bbe239fa2cabfa6b3296a2.png?itok=Dn5Pc9aA"),
            Map.entry("Carter Pinnington", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/carter-pinnington-2025-26-headshot_dafce186e1de0b79e4ed0e341346eb4b.jpg?itok=t-0D7RYP"),
            Map.entry("Amara Nallo", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/amara-nallo-2526-headshot-non-transparent_dac884e32412a16290150a6010a7e3b8.jpg?itok=b9Amf99b"),
            Map.entry("Wellity Lucky", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/wellity-lucky-2025-26-headshot_61095d598f098e8cd5bfed7346304451.jpg?itok=BP4mPZH8"),
            Map.entry("Kieran Morrison", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/kieran-morrison-2025-26-headshot_ee6727b4c93cbfe1bf1a925a875a5bdd.jpg?itok=24NONFUH"),
            Map.entry("Tommy Pilling", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-09/tommy-pilling-headshot-2025-26_57d5ab633fb57e56ba1d513af5fcf00c.jpg?itok=irvCy_s5"),
            Map.entry("Michael Laffey", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/michael-laffey-2025-26-headshot_aaa495325c2f3a2cb56e593467eb31df.jpg?itok=pb2a9tzp"),
            Map.entry("Luca Gunter", "https://resources.thfc.pulselive.com/photo-resources/2026/01/14/8b1a9809-b428-4800-9d7f-0e0a5784674d/u21_profiles_202526_lucagunter.png?width=363&height=433"),
            Map.entry("James Rowswell", "https://resources.thfc.pulselive.com/photo-resources/2026/01/14/d4b4956b-658a-4e8b-972d-cd70ec24ce29/u21_profiles_202526_jamesrowswell.png?width=363&height=433"),
            Map.entry("Rio Kyerematen", "https://resources.thfc.pulselive.com/photo-resources/2026/01/14/8fbaaefe-d80a-490b-8bc5-28ce5836c4f7/u21_profiles_202526_riokyerematen.png?width=363&height=433"),
            Map.entry("Tye Hall", "https://resources.thfc.pulselive.com/photo-resources/2026/01/15/caf44821-7061-4303-820c-01283cc708ee/u18_profiles_202526_tyehall.png?width=363&height=433"),
            Map.entry("Tynan Thompson", "https://resources.thfc.pulselive.com/photo-resources/2026/01/15/8447c773-7107-4000-897e-280f3b995e2f/u18_profiles_202526_tynanthompson.png?width=363&height=433"),
            Map.entry("Luca Williams-Barnett", "https://resources.thfc.pulselive.com/photo-resources/2026/01/15/28975f94-767c-4d89-b467-b77a20813843/u18_profiles_202526_lucawilliamsbarnett.png?width=363&height=433"),
            // 近期转会 — PL 传统 CDN (premierleague/photos/...) 还是旧球衣，强制走 premierleague25 (2025/26 赛季目录) 拿到新俱乐部照
            Map.entry("Antoine Semenyo", "https://resources.premierleague.com/premierleague25/photos/players/110x140/437730.png"),
            Map.entry("João Pedro", "https://resources.premierleague.com/premierleague25/photos/players/110x140/475168.png"),
            Map.entry("Joao Pedro", "https://resources.premierleague.com/premierleague25/photos/players/110x140/475168.png"),
            Map.entry("Jack Grealish", "https://resources.premierleague.com/premierleague25/photos/players/110x140/114283.png"),
            Map.entry("Bryan Mbeumo", "https://resources.premierleague.com/premierleague25/photos/players/110x140/446008.png"),      // 2025 夏窗 Brentford → Man Utd
            Map.entry("Matheus Cunha", "https://resources.premierleague.com/premierleague25/photos/players/110x140/430871.png"),     // 2025 夏窗 Wolves → Man Utd
            Map.entry("Jadon Sancho", "https://resources.premierleague.com/premierleague25/photos/players/110x140/209243.png"),       // 2025/26 租借 Man Utd → Aston Villa
            Map.entry("Marcus Rashford", "https://resources.premierleague.com/premierleague25/photos/players/110x140/176297.png"),   // 2025/26 租借 Man Utd → Barcelona (非 PL, CDN 只有 Man Utd 旧照兜底)
            Map.entry("Rasmus Højlund", "https://resources.premierleague.com/premierleague25/photos/players/110x140/497894.png"),    // 2025/26 租借 Man Utd → Napoli (非 PL, CDN 只有 Man Utd 旧照兜底)
            Map.entry("Rasmus Hojlund", "https://resources.premierleague.com/premierleague25/photos/players/110x140/497894.png")
    );

    /**
     * 最近冬窗/夏窗转会的球员：PL CDN `p{optaId}.png` 仍然返回旧俱乐部球衣的照片（联盟没来得及补拍）。
     * 对这些球员，每次 squad sync 都强制重新解析 photo_url，优先走 Wikipedia（社区编辑会跟进新俱乐部写真），
     * Wikipedia 兜不住再退回 PL 搜索 / CDN（至少保证不缺图）。
     *
     * 注意：
     *   - 这里放的是显示名 (squad 端返回的 displayName / entity.name)。带重音符号的名字会额外判一次 stripAccents。
     *   - 能覆盖 CLUB_PHOTO_OVERRIDES 的不需要再放进来（override map 会直接命中）。
     */
    private static final Set<String> FORCE_WIKIPEDIA_REFRESH = Set.of(
            // —— 2024/25 及早期持续转会（intra-PL permanent，但 PL CDN 未必及时更新）——
            "João Pedro",               // 2024 夏窗 Brighton → Chelsea
            "Joao Pedro",               // accent-stripped
            // —— 2025/26 夏窗 intra-PL permanent ——
            "Bryan Mbeumo",             // Brentford → Man Utd
            "Matheus Cunha",            // Wolves → Man Utd
            // —— 2025/26 冬窗 intra-PL permanent ——
            "Antoine Semenyo",          // Bournemouth → Man City
            // —— 2025/26 loans OUT of PL (PL CDN 不再更新，必须 Wikipedia)——
            "Marcus Rashford",          // Man Utd → Barcelona
            "Rasmus Højlund",           // Man Utd → Napoli
            "Rasmus Hojlund",           // accent-stripped
            "Tyrell Malacia",           // Man Utd → PSV
            "Leon Bailey",              // Aston Villa → Roma
            "Matt O'Riley",             // Brighton → Marseille
            "Matt O’Riley",             // curly-apostrophe variant
            "Milan Aleksić",            // Sunderland → Cracovia
            "Milan Aleksic",            // accent-stripped
            "Eric da Silva Moreira",    // Nottingham Forest → Rio Ave
            // —— 2025/26 intra-PL loans (father club 可能不刷新 opta 照片，强制 Wikipedia)——
            "Jack Grealish",            // Man City → Everton
            "Jadon Sancho",             // Man Utd → Aston Villa
            "Harvey Elliott",           // Liverpool → Aston Villa
            "Reiss Nelson",             // Arsenal → Fulham
            "Igor Julio",               // Brighton → West Ham
            "Axel Disasi",              // Chelsea → West Ham
            "Facundo Buonanotte",       // Brighton → Chelsea
            "Tyrique George",           // Chelsea → Everton
            "Marc Guiu",                // Chelsea → Sunderland
            "Evann Guessand",           // Aston Villa → Crystal Palace
            // —— 2025/26 academy/reserve loans (in DB so worth refreshing)——
            "Teddy Sharman-Lowe",
            "Harvey Davies",
            "Jack Moorhouse",
            "Mamadou Sarr",
            "Charlie Crew",
            "Max Alleyne",
            "Sverre Nypan",
            "Harrison Armstrong",
            "Lewis Orford"
    );

    private static final Map<String, String> OPTA_OVERRIDES = Map.ofEntries(
            Map.entry("Gabriel", "p226597"),
            Map.entry("Sávio", "p510281"),
            Map.entry("Savio", "p510281"),
            Map.entry("Nico O'Reilly", "p472769"),
            Map.entry("Nico O’Reilly", "p472769"),
            Map.entry("Chido Obi", "p596047"),
            Map.entry("Chido Obi-Martin", "p596047"),
            Map.entry("Ife Ibrahim", "p616068"),
            Map.entry("Ifeoluwa Ibrahim", "p616068"),
            Map.entry("Ceadach O'Neill", "p645551"),
            Map.entry("Ceadach O’Neill", "p645551")
    );

    private final HttpClientUtil http;
    private final WikipediaPlayerPhotoProvider wikipediaPlayerPhotoProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    // Cache positive hits only so transient network failures do not get stuck as permanent misses.
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String findPhotoUrl(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        String key = playerName.trim();
        String hit = cache.get(key);
        if (hit != null) {
            return hit;
        }

        String overridePhoto = resolveOverridePhoto(key);
        if (overridePhoto != null) {
            cache.put(key, overridePhoto);
            return overridePhoto;
        }

        String photo = searchByAlias(key);
        if (photo == null) {
            photo = searchByName(key);
        }
        if (photo == null) {
            String normalized = stripAccents(key);
            if (!normalized.equals(key)) {
                photo = searchByName(normalized);
            }
        }

        if (photo == null) {
            String[] parts = key.split("\\s+");
            if (parts.length > 1) {
                String lastName = stripAccents(parts[parts.length - 1]);
                photo = searchByName(lastName);
            }
        }

        if (photo == null) {
            photo = wikipediaPlayerPhotoProvider.findPhotoUrl(key);
        }

        if (photo != null) {
            cache.put(key, photo);
        }
        return photo;
    }

    public String findUsablePhotoUrl(String playerName, String candidateUrl) {
        if (candidateUrl != null && !candidateUrl.isBlank()) {
            String url = candidateUrl.trim();
            if (http.headOk(url, IMAGE_HEADERS)) {
                return url;
            }

            String opta = extractOptaFromPhotoUrl(url);
            if (opta != null) {
                String resolved = resolvePhotoUrl(opta);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        return findPhotoUrl(playerName);
    }

    public String findPhotoUrlByOfficialId(String officialId) {
        if (officialId == null || officialId.isBlank()) {
            return null;
        }
        return resolvePhotoUrl(officialId.startsWith("p") ? officialId : "p" + officialId);
    }

    /**
     * 暴露强制刷新名单给管理端使用（管理端的 "refresh-transfer-photos" 会按名扫 players 表，
     * 对其中命中的行直接重写 photo_url — 适用于被租借到非 PL 俱乐部、Pulselive squad 端
     * 不再返回该球员、从而 squad sync 路径摸不到的情况）。
     */
    public Set<String> getForceRefreshPlayerNames() {
        return FORCE_WIKIPEDIA_REFRESH;
    }

    /**
     * 判定该球员是否属于「近期转会、PL CDN 还拿不到新俱乐部照」的名单。
     * 这类球员的 photo_url 应当每次 squad sync 强制刷新，而不是沿用 DB 里已经存在的旧值。
     */
    public boolean isForceWikipediaRefresh(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        String key = playerName.trim();
        if (FORCE_WIKIPEDIA_REFRESH.contains(key)) {
            return true;
        }
        return FORCE_WIKIPEDIA_REFRESH.contains(stripAccents(key));
    }

    /**
     * 强制刷新解析：显式 override → Wikipedia → PL 搜索。绕开内部正向缓存，
     * 让冬窗转会后 DB 里被 PL CDN 旧图污染的 photo_url 有机会被替换掉。
     *
     * override 放在最前：社区维护的 Wikipedia 首图对租借/冬窗球员常年落后（返回旧俱乐部 infobox 照），
     * 比如 Jack Grealish 的 Wikipedia 主图还是 2024 曼城照。premierleague25 CDN 目录（赛季专用）才是可靠来源。
     */
    public String findForceRefreshPhoto(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        String key = playerName.trim();

        String override = resolveOverridePhoto(key);
        if (override != null && !override.isBlank()) {
            return override;
        }

        try {
            String wiki = wikipediaPlayerPhotoProvider.findPhotoUrl(key);
            if (wiki != null && !wiki.isBlank()) {
                return wiki;
            }
            String accentless = stripAccents(key);
            if (!accentless.equals(key)) {
                wiki = wikipediaPlayerPhotoProvider.findPhotoUrl(accentless);
                if (wiki != null && !wiki.isBlank()) {
                    return wiki;
                }
            }
        } catch (Exception e) {
            log.debug("[PlPhoto] force-refresh wikipedia failed for {}: {}", key, e.getMessage());
        }

        String photo = searchByAlias(key);
        if (photo == null) {
            photo = searchByName(key);
        }
        if (photo == null) {
            String normalized = stripAccents(key);
            if (!normalized.equals(key)) {
                photo = searchByName(normalized);
            }
        }
        // force-refresh 路径下：若 Pulselive 返回了 legacy（旧赛季）URL，尝试切到
        // premierleague25（当前赛季）的相同 opta，拿到更新过的球衣照。resolvePhotoUrl
        // 默认偏好 legacy 是为了通用路径的稳定性，但此处我们明确要新赛季照片。
        if (photo != null && photo.contains("/premierleague/photos/players/250x250/p")) {
            String opta = extractOptaFromPhotoUrl(photo);
            if (opta != null) {
                String numeric = opta.startsWith("p") ? opta.substring(1) : opta;
                String currentUrl = String.format(CURRENT_PHOTO_URL_TMPL, numeric);
                if (http.headOk(currentUrl, IMAGE_HEADERS)) {
                    log.info("[PlPhoto] force-refresh upgrade legacy->premierleague25 for '{}': {} -> {}",
                            key, photo, currentUrl);
                    return currentUrl;
                }
            }
        }
        return photo;
    }

    private String searchByName(String name) {
        try {
            String url = String.format(SEARCH_URL, URLEncoder.encode(name, StandardCharsets.UTF_8));
            String body = http.getWithHeaders(url, Map.of(
                    "Origin", "https://www.premierleague.com",
                    "Referer", "https://www.premierleague.com/",
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            ));
            if (body == null || body.isEmpty()) {
                return null;
            }

            JsonNode root = mapper.readTree(body);
            JsonNode hits = root.path("hits").path("hit");
            if (!hits.isArray() || hits.size() == 0) {
                log.debug("[PlPhoto] no hits for {}", name);
                return null;
            }

            JsonNode picked = pickBestMatch(hits, name);
            String opta = picked.path("response").path("altIds").path("opta").asText(null);
            if (opta == null || opta.isBlank()) {
                return null;
            }

            String photoUrl = resolvePhotoUrl(opta);
            log.debug("[PlPhoto] {} -> {} -> {}", name, opta, photoUrl);
            return photoUrl;
        } catch (Exception e) {
            log.debug("[PlPhoto] searchByName failed for {}: {}", name, e.getMessage());
            return null;
        }
    }

    private String searchByAlias(String name) {
        String alias = SEARCH_ALIASES.get(name);
        if (alias == null) {
            alias = SEARCH_ALIASES.get(stripAccents(name));
        }
        if (alias == null || alias.equalsIgnoreCase(name)) {
            return null;
        }
        return searchByName(alias);
    }

    private String resolveOverridePhoto(String name) {
        String clubPhoto = CLUB_PHOTO_OVERRIDES.get(name);
        if (clubPhoto == null) {
            clubPhoto = CLUB_PHOTO_OVERRIDES.get(stripAccents(name));
        }
        if (clubPhoto != null) {
            return clubPhoto;
        }

        String opta = OPTA_OVERRIDES.get(name);
        if (opta == null) {
            opta = OPTA_OVERRIDES.get(stripAccents(name));
        }
        if (opta == null) {
            return null;
        }
        return resolvePhotoUrl(opta);
    }

    private String resolvePhotoUrl(String opta) {
        String legacyUrl = String.format(LEGACY_PHOTO_URL_TMPL, opta);
        if (http.headOk(legacyUrl, IMAGE_HEADERS)) {
            return legacyUrl;
        }

        String numericOpta = opta.startsWith("p") ? opta.substring(1) : opta;
        String currentUrl = String.format(CURRENT_PHOTO_URL_TMPL, numericOpta);
        if (http.headOk(currentUrl, IMAGE_HEADERS)) {
            return currentUrl;
        }

        String smallUrl = String.format(CURRENT_SMALL_PHOTO_URL_TMPL, numericOpta);
        if (http.headOk(smallUrl, IMAGE_HEADERS)) {
            return smallUrl;
        }

        return null;
    }

    private String extractOptaFromPhotoUrl(String url) {
        if (!url.contains("resources.premierleague.com")
                && !url.contains("resources.premierleague.pulselive.com")) {
            return null;
        }

        int slash = url.lastIndexOf('/');
        String fileName = slash >= 0 ? url.substring(slash + 1) : url;
        int query = fileName.indexOf('?');
        if (query >= 0) {
            fileName = fileName.substring(0, query);
        }
        int dot = fileName.indexOf('.');
        String id = dot >= 0 ? fileName.substring(0, dot) : fileName;
        if (!id.matches("p?\\d+")) {
            return null;
        }
        return id.startsWith("p") ? id : "p" + id;
    }

    private static String stripAccents(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD);
        return normalized.replaceAll("\\p{M}", "")
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u0131', 'i')
                .replace('\u0141', 'L')
                .replace('\u0142', 'l')
                .replace('\u00d8', 'O')
                .replace('\u00f8', 'o')
                .replace('\u0110', 'D')
                .replace('\u0111', 'd')
                .replace('\u015e', 'S')
                .replace('\u015f', 's')
                .replace('\u0218', 'S')
                .replace('\u0219', 's')
                .replace('\u021a', 'T')
                .replace('\u021b', 't')
                .replace("酶", "o")
                .replace("脴", "O");
    }

    private JsonNode pickBestMatch(JsonNode hits, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        for (JsonNode hit : hits) {
            String display = hit.path("response").path("name").path("display").asText("");
            if (display.equalsIgnoreCase(query)) {
                return hit;
            }
        }
        for (JsonNode hit : hits) {
            String display = hit.path("response").path("name").path("display")
                    .asText("")
                    .toLowerCase(Locale.ROOT);
            if (display.contains(q) || q.contains(display)) {
                return hit;
            }
        }
        return hits.get(0);
    }
}
