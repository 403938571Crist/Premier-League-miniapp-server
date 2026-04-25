package com.premierleague.server.provider;

import com.premierleague.server.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlPhotoProviderTest {

    private HttpClientUtil http;
    private WikipediaPlayerPhotoProvider wikipediaPlayerPhotoProvider;
    private PlPhotoProvider provider;

    @BeforeEach
    void setUp() {
        http = mock(HttpClientUtil.class);
        wikipediaPlayerPhotoProvider = mock(WikipediaPlayerPhotoProvider.class);
        provider = new PlPhotoProvider(http, wikipediaPlayerPhotoProvider);
    }

    @Test
    void keepsLegacy250PhotoWhenAvailable() {
        stubSearch("Eli Junior Kroupi", "p560262");
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p560262.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/560262.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(true);

        assertEquals(legacyUrl, provider.findPhotoUrl("Eli Junior Kroupi"));
        verify(http, never()).headOk(currentUrl, imageHeaders());
    }

    @Test
    void fallsBackToCurrentSeasonPhotoWhenLegacy250PhotoIsMissing() {
        stubSearch("Eli Junior Kroupi", "p560262");
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p560262.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/560262.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(true);

        assertEquals(currentUrl, provider.findPhotoUrl("Eli Junior Kroupi"));
    }

    @Test
    void replacesBrokenLegacyCandidateWithoutSearchingAgain() {
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p560262.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/560262.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(true);

        assertEquals(currentUrl, provider.findUsablePhotoUrl("Eli Junior Kroupi", legacyUrl));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
    }

    @Test
    void fallsBackToWikipediaPhotoWhenOfficialAssetDoesNotExist() {
        stubSearch("Missing Photo Prospect", "p616286");
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p616286.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/616286.png";
        String smallUrl = "https://resources.premierleague.com/premierleague25/photos/players/40x40/616286.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(smallUrl, imageHeaders())).thenReturn(false);
        when(wikipediaPlayerPhotoProvider.findPhotoUrl("Missing Photo Prospect"))
                .thenReturn("https://upload.wikimedia.org/wikipedia/commons/9/99/Missing_Photo_Prospect_2026.jpg");

        assertEquals(
                "https://upload.wikimedia.org/wikipedia/commons/9/99/Missing_Photo_Prospect_2026.jpg",
                provider.findPhotoUrl("Missing Photo Prospect")
        );
    }

    @Test
    void resolvesKnownOptaOverrideForChidoObiMartin() {
        String legacyUrl = "https://resources.premierleague.com/premierleague/photos/players/250x250/p596047.png";
        String currentUrl = "https://resources.premierleague.com/premierleague25/photos/players/110x140/596047.png";
        when(http.headOk(legacyUrl, imageHeaders())).thenReturn(false);
        when(http.headOk(currentUrl, imageHeaders())).thenReturn(true);

        assertEquals(currentUrl, provider.findPhotoUrl("Chido Obi-Martin"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForIfeIbrahim() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3428_ujHhxO3S_WEB_kwma9mqg.png?h=ad73a5fe&auto=webp&itok=W-1S38Hs";

        assertEquals(officialUrl, provider.findPhotoUrl("Ife Ibrahim"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForIfeoluwaIbrahim() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3428_ujHhxO3S_WEB_kwma9mqg.png?h=ad73a5fe&auto=webp&itok=W-1S38Hs";

        assertEquals(officialUrl, provider.findPhotoUrl("Ifeoluwa Ibrahim"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForCurlyApostropheClubName() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3760_t05WektC_WEB_o0acbj1f.png?h=ad73a5fe&auto=webp&itok=73cjXwDl";

        assertEquals(officialUrl, provider.findPhotoUrl("Ceadach O’Neill"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForRyanKavumaMcQueen() {
        String officialUrl = "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/news/2025/09/23/Ryan_Kavuma-McQueen_Chelsea";

        assertEquals(officialUrl, provider.findPhotoUrl("Ryan Kavuma-McQueen"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForShumairaMheuka() {
        String officialUrl = "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/news/2024/10/23/Mheuka_U21_CL1_9104_qPPw1iVP";

        assertEquals(officialUrl, provider.findPhotoUrl("Shumaira Mheuka"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForAlexeiRojas() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3202_GYGQK3bS_WEB_h4lfkes3.png?h=ad73a5fe&auto=webp&itok=tYtqvB3T";

        assertEquals(officialUrl, provider.findPhotoUrl("Alexei Rojas"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForJackPorter() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3220_ZiknMcM7_WEB_gxfyael4.png?h=ad73a5fe&auto=webp&itok=7uleo71I";

        assertEquals(officialUrl, provider.findPhotoUrl("Jack Porter"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForHarrisonDudziak() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3526_q6WLqETJ_WEB_wz4z8rqm.png?h=ad73a5fe&auto=webp&itok=0GtKk44J";

        assertEquals(officialUrl, provider.findPhotoUrl("Harrison Dudziak"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForJoshNichols() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3574_gZXPQ7c9_WEB_wwznb0mc.png?h=ad73a5fe&auto=webp&itok=OpNAMF0n";

        assertEquals(officialUrl, provider.findPhotoUrl("Josh Nichols"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForKhariRanson() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3916_GrU0skHz_WEB_gfumvqz5.png?h=ad73a5fe&auto=webp&itok=JzVw2A3N";

        assertEquals(officialUrl, provider.findPhotoUrl("Khari Ranson"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForLandonEmenalo() {
        String officialUrl = "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/Academy/Stock%202025-26/U21/Emenalo_U21_IW2_1525";

        assertEquals(officialUrl, provider.findPhotoUrl("Landon Emenalo"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForJesseDerry() {
        String officialUrl = "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/Academy/Jesse%20Derry%20signs/CL2_1969_erbIPMgF";

        assertEquals(officialUrl, provider.findPhotoUrl("Jesse Derry"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForReggieWalsh() {
        String officialUrl = "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/news/2025/10/24/Walsh_contract_2025_CL1_6693_aBmqyk9Y";

        assertEquals(officialUrl, provider.findPhotoUrl("Reggie Walsh"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForBrandoBaileyJoseph() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/brando_u9e2sg3l.png?h=3c8f2bed&auto=webp&itok=-TE7-FfR";

        assertEquals(officialUrl, provider.findPhotoUrl("Brando Bailey-Joseph"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForJadenDixon() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/jaden-dixon-profile_nuackxvy.jpg?h=68d83a20&auto=webp&itok=sCtiNCUH";

        assertEquals(officialUrl, provider.findPhotoUrl("Jaden Dixon"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForMarliSalmon() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/salmon_fwqwbleq.png?h=6dff888f&auto=webp&itok=o5HVbfm5";

        assertEquals(officialUrl, provider.findPhotoUrl("Marli Salmon"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForMaxMerrick() {
        String officialUrl = "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/restricted/2026/Merrick%20contract/Merrick_contract_2026_CL1_4646_BLpGCZvL";

        assertEquals(officialUrl, provider.findPhotoUrl("Max Merrick"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForOllieHarrison() {
        String officialUrl = "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/news/2025/09/23/Ollie_Harrison_Chelsea_Under-19s_UEFA_Youth_League";

        assertEquals(officialUrl, provider.findPhotoUrl("Ollie Harrison"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForBenditoMantato() {
        String officialUrl = "https://assets.manutd.com/AssetPicker/images/0/0/22/246/1504959/Bendito_Mantato1759498479811_large.jpg";

        assertEquals(officialUrl, provider.findPhotoUrl("Bendito Mantato"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForGodwillKukonki() {
        String officialUrl = "https://assets.manutd.com/AssetPicker/images/0/0/22/246/1504970/Godwill_Kukonki1759837274632_large.jpg";

        assertEquals(officialUrl, provider.findPhotoUrl("Godwill Kukonki"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForJimThwaites() {
        String officialUrl = "https://assets.manutd.com/AssetPicker/images/0/0/22/246/1504981/Jim_Thwaites1759505599926_large.jpg";

        assertEquals(officialUrl, provider.findPhotoUrl("Jim Thwaites"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForSheaLacey() {
        String officialUrl = "https://assets.manutd.com/AssetPicker/images/0/0/22/246/1504994/Shea_Lacey1759923765054_large.jpg";

        assertEquals(officialUrl, provider.findPhotoUrl("Shea Lacey"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForJaydenDanns() {
        String officialUrl = "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-02/Jayden-Danns-030225_99619fa3a9b38a4fa8ba04a5d10d62eb.jpg?itok=c39cOChQ";

        assertEquals(officialUrl, provider.findPhotoUrl("Jayden Danns"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForAlekseiFedorushchenko() {
        String officialUrl = "https://www.arsenal.com/sites/default/files/styles/desktop_16x9/public/images/Z91_3202_GYGQK3bS_WEB_h4lfkes3.png?h=ad73a5fe&auto=webp&itok=tYtqvB3T";

        assertEquals(officialUrl, provider.findPhotoUrl("Aleksei Fedorushchenko"));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForAdditionalTottenhamAcademyPlayers() {
        Map<String, String> officialUrls = Map.ofEntries(
                Map.entry("Luca Gunter", "https://resources.thfc.pulselive.com/photo-resources/2026/01/14/8b1a9809-b428-4800-9d7f-0e0a5784674d/u21_profiles_202526_lucagunter.png?width=363&height=433"),
                Map.entry("James Rowswell", "https://resources.thfc.pulselive.com/photo-resources/2026/01/14/d4b4956b-658a-4e8b-972d-cd70ec24ce29/u21_profiles_202526_jamesrowswell.png?width=363&height=433"),
                Map.entry("Rio Kyerematen", "https://resources.thfc.pulselive.com/photo-resources/2026/01/14/8fbaaefe-d80a-490b-8bc5-28ce5836c4f7/u21_profiles_202526_riokyerematen.png?width=363&height=433"),
                Map.entry("Tye Hall", "https://resources.thfc.pulselive.com/photo-resources/2026/01/15/caf44821-7061-4303-820c-01283cc708ee/u18_profiles_202526_tyehall.png?width=363&height=433"),
                Map.entry("Tynan Thompson", "https://resources.thfc.pulselive.com/photo-resources/2026/01/15/8447c773-7107-4000-897e-280f3b995e2f/u18_profiles_202526_tynanthompson.png?width=363&height=433"),
                Map.entry("Luca Williams-Barnett", "https://resources.thfc.pulselive.com/photo-resources/2026/01/15/28975f94-767c-4d89-b467-b77a20813843/u18_profiles_202526_lucawilliamsbarnett.png?width=363&height=433")
        );

        assertAll(officialUrls.entrySet().stream()
                .map(entry -> () -> assertEquals(entry.getValue(), provider.findPhotoUrl(entry.getKey()))));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForAdditionalLiverpoolAcademyPlayers() {
        Map<String, String> officialUrls = Map.ofEntries(
                Map.entry("Kornel Misciur", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/kornel-misciur-2025-26-headshot_98cea4014b973932ce225add39eefc78.jpg?itok=7dp3wMya"),
                Map.entry("Rhys Williams", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-09/rhys-williams-2025-26-v2_8cbdcb5e65bbe239fa2cabfa6b3296a2.png?itok=Dn5Pc9aA"),
                Map.entry("Carter Pinnington", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/carter-pinnington-2025-26-headshot_dafce186e1de0b79e4ed0e341346eb4b.jpg?itok=t-0D7RYP"),
                Map.entry("Amara Nallo", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/amara-nallo-2526-headshot-non-transparent_dac884e32412a16290150a6010a7e3b8.jpg?itok=b9Amf99b"),
                Map.entry("Wellity Lucky", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/wellity-lucky-2025-26-headshot_61095d598f098e8cd5bfed7346304451.jpg?itok=BP4mPZH8"),
                Map.entry("Kieran Morrison", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/kieran-morrison-2025-26-headshot_ee6727b4c93cbfe1bf1a925a875a5bdd.jpg?itok=24NONFUH"),
                Map.entry("Tommy Pilling", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-09/tommy-pilling-headshot-2025-26_57d5ab633fb57e56ba1d513af5fcf00c.jpg?itok=irvCy_s5"),
                Map.entry("Michael Laffey", "https://backend.liverpoolfc.com/sites/default/files/styles/lg/public/2025-08/michael-laffey-2025-26-headshot_aaa495325c2f3a2cb56e593467eb31df.jpg?itok=pb2a9tzp")
        );

        assertAll(officialUrls.entrySet().stream()
                .map(entry -> () -> assertEquals(entry.getValue(), provider.findPhotoUrl(entry.getKey()))));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    @Test
    void prefersVerifiedOfficialClubPhotoForRemainingBigSixPlayers() {
        Map<String, String> officialUrls = Map.ofEntries(
                Map.entry("Kaden Braithwaite", "https://www.mancity.com/meta/media/upsc25hd/braithwaite.jpg?width=1620"),
                Map.entry("Kian Noble", "https://www.mancity.com/meta/media/k4nbqjgb/kian-noble-celebration.jpg?width=976"),
                Map.entry("Floyd Samba", "https://www.mancity.com/meta/media/bhunvole/floyd-samba.jpg?width=1620"),
                Map.entry("Ryan McAidoo", "https://www.mancity.com/meta/media/ug1b54yf/ryan-mcaidoo.jpg"),
                Map.entry("Charlie Gray", "https://www.mancity.com/meta/media/y2rirjfx/gray.jpg?width=1620"),
                Map.entry("Tyrone Samba", "https://www.mancity.com/meta/media/nwdh3t34/tyrone-samba-yl.jpg"),
                Map.entry("Jack Moorhouse", "https://assets.manutd.com/AssetPicker/images/0/0/23/45/1518917/GettyImages_22329200311762878413977_large.jpg"),
                Map.entry("Charlie Holland", "https://res.cloudinary.com/chelsea-production/image/upload/c_fit,h_630,w_1200/v1/editorial/Academy/Under-18%2025-26%20season/GettyImages-2229427989")
        );

        assertAll(officialUrls.entrySet().stream()
                .map(entry -> () -> assertEquals(entry.getValue(), provider.findPhotoUrl(entry.getKey()))));
        verify(http, never()).getWithHeaders(anyString(), anyMap());
        verify(wikipediaPlayerPhotoProvider, never()).findPhotoUrl(anyString());
    }

    private void stubSearch(String displayName, String opta) {
        String body = """
                {"hits":{"hit":[{"response":{"altIds":{"opta":"%s"},"name":{"display":"%s"}}}]}}
                """.formatted(opta, displayName);
        when(http.getWithHeaders(anyString(), anyMap())).thenReturn(body);
    }

    private Map<String, String> imageHeaders() {
        return Map.of(
                "Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                "Referer", "https://www.premierleague.com/",
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        );
    }
}
