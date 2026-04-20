package com.premierleague.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Player entity
 */
@Entity
@Table(name = "players", indexes = {
    @Index(name = "idx_team_id", columnList = "teamId"),
    @Index(name = "idx_position", columnList = "position"),
    @Index(name = "idx_api_id", columnList = "apiId", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    private static final Map<String, String> NATIONALITY_MAP = Map.ofEntries(
            Map.entry("England", "英格兰"),
            Map.entry("Scotland", "苏格兰"),
            Map.entry("Wales", "威尔士"),
            Map.entry("Northern Ireland", "北爱尔兰"),
            Map.entry("Ireland", "爱尔兰"),
            Map.entry("Spain", "西班牙"),
            Map.entry("Portugal", "葡萄牙"),
            Map.entry("France", "法国"),
            Map.entry("Germany", "德国"),
            Map.entry("Italy", "意大利"),
            Map.entry("Netherlands", "荷兰"),
            Map.entry("Belgium", "比利时"),
            Map.entry("Denmark", "丹麦"),
            Map.entry("Sweden", "瑞典"),
            Map.entry("Norway", "挪威"),
            Map.entry("Finland", "芬兰"),
            Map.entry("Poland", "波兰"),
            Map.entry("Czech Republic", "捷克"),
            Map.entry("Austria", "奥地利"),
            Map.entry("Switzerland", "瑞士"),
            Map.entry("Croatia", "克罗地亚"),
            Map.entry("Serbia", "塞尔维亚"),
            Map.entry("Slovenia", "斯洛文尼亚"),
            Map.entry("Ukraine", "乌克兰"),
            Map.entry("Romania", "罗马尼亚"),
            Map.entry("Turkey", "土耳其"),
            Map.entry("Greece", "希腊"),
            Map.entry("Brazil", "巴西"),
            Map.entry("Argentina", "阿根廷"),
            Map.entry("Uruguay", "乌拉圭"),
            Map.entry("Colombia", "哥伦比亚"),
            Map.entry("Ecuador", "厄瓜多尔"),
            Map.entry("Paraguay", "巴拉圭"),
            Map.entry("Mexico", "墨西哥"),
            Map.entry("United States", "美国"),
            Map.entry("Canada", "加拿大"),
            Map.entry("Australia", "澳大利亚"),
            Map.entry("Japan", "日本"),
            Map.entry("Korea Republic", "韩国"),
            Map.entry("South Korea", "韩国"),
            Map.entry("China PR", "中国"),
            Map.entry("Morocco", "摩洛哥"),
            Map.entry("Algeria", "阿尔及利亚"),
            Map.entry("Tunisia", "突尼斯"),
            Map.entry("Egypt", "埃及"),
            Map.entry("Senegal", "塞内加尔"),
            Map.entry("Ghana", "加纳"),
            Map.entry("Nigeria", "尼日利亚"),
            Map.entry("Mali", "马里"),
            Map.entry("Guinea", "几内亚"),
            Map.entry("Cameroon", "喀麦隆"),
            Map.entry("Cote d'Ivoire", "科特迪瓦"),
            Map.entry("Ivory Coast", "科特迪瓦")
    );

    private static final Map<String, String> POSITION_LABEL_MAP = Map.ofEntries(
            Map.entry("goalkeeper", "\u95e8\u5c06"),
            Map.entry("defence", "\u540e\u536b"),
            Map.entry("defender", "\u540e\u536b"),
            Map.entry("centre back", "\u4e2d\u540e\u536b"),
            Map.entry("center back", "\u4e2d\u540e\u536b"),
            Map.entry("central defender", "\u4e2d\u540e\u536b"),
            Map.entry("left back", "\u5de6\u540e\u536b"),
            Map.entry("right back", "\u53f3\u540e\u536b"),
            Map.entry("wing back", "\u8fb9\u7ffc\u536b"),
            Map.entry("left wing back", "\u5de6\u8fb9\u7ffc\u536b"),
            Map.entry("right wing back", "\u53f3\u8fb9\u7ffc\u536b"),
            Map.entry("midfield", "\u4e2d\u573a"),
            Map.entry("midfielder", "\u4e2d\u573a"),
            Map.entry("defensive midfield", "\u540e\u8170"),
            Map.entry("central midfield", "\u4e2d\u524d\u536b"),
            Map.entry("attacking midfield", "\u524d\u8170"),
            Map.entry("left midfield", "\u5de6\u524d\u536b"),
            Map.entry("right midfield", "\u53f3\u524d\u536b"),
            Map.entry("offence", "\u524d\u950b"),
            Map.entry("offense", "\u524d\u950b"),
            Map.entry("attack", "\u524d\u950b"),
            Map.entry("attacker", "\u524d\u950b"),
            Map.entry("forward", "\u524d\u950b"),
            Map.entry("centre forward", "\u4e2d\u950b"),
            Map.entry("center forward", "\u4e2d\u950b"),
            Map.entry("striker", "\u524d\u950b"),
            Map.entry("second striker", "\u5f71\u950b"),
            Map.entry("left winger", "\u5de6\u8fb9\u950b"),
            Map.entry("right winger", "\u53f3\u8fb9\u950b"),
            Map.entry("winger", "\u8fb9\u950b")
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_id")
    private Long apiId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String chineseName;

    @Column(length = 500)
    private String photoUrl;

    @Column(length = 10)
    private String shirtNumber;

    @Column(length = 20)
    private String position;

    @Column(length = 20)
    private String chinesePosition;

    @Column(length = 50)
    private String nationality;

    private LocalDate dateOfBirth;

    @Transient
    private Integer age;

    private Integer height;

    private Integer weight;

    @Column(length = 10)
    private String foot;

    private LocalDate contractUntil;

    private Long marketValue;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Integer getAge() {
        if (dateOfBirth == null) {
            return null;
        }
        return LocalDate.now().getYear() - dateOfBirth.getYear();
    }

    @Transient
    public String getChineseNationality() {
        if (nationality == null || nationality.isBlank()) {
            return "";
        }
        String normalizedNationality = nationality.trim();
        if ("Russia".equalsIgnoreCase(normalizedNationality)
                || "Russian Federation".equalsIgnoreCase(normalizedNationality)) {
            return "\u4fc4\u7f57\u65af";
        }
        return NATIONALITY_MAP.getOrDefault(normalizedNationality, normalizedNationality);
    }

    @Transient
    public String getNationalityLabel() {
        return getChineseNationality();
    }

    @Transient
    public String getPositionLabel() {
        String translatedChinesePosition = translatePosition(chinesePosition);
        if (!translatedChinesePosition.isEmpty()) {
            return translatedChinesePosition;
        }
        return translatePosition(position);
    }

    @Transient
    public String getPhoto() {
        return photoUrl;
    }

    private String translatePosition(String rawPosition) {
        if (rawPosition == null || rawPosition.isBlank()) {
            return "";
        }
        if (containsChinese(rawPosition)) {
            return rawPosition;
        }

        String normalized = rawPosition.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        String direct = POSITION_LABEL_MAP.get(normalized);
        if (direct != null) {
            return direct;
        }
        if (normalized.contains("goal")) {
            return "\u95e8\u5c06";
        }
        if (normalized.contains("back")) {
            if (normalized.contains("left")) {
                return "\u5de6\u540e\u536b";
            }
            if (normalized.contains("right")) {
                return "\u53f3\u540e\u536b";
            }
            return "\u540e\u536b";
        }
        if (normalized.contains("midfield")) {
            if (normalized.contains("defensive")) {
                return "\u540e\u8170";
            }
            if (normalized.contains("attacking")) {
                return "\u524d\u8170";
            }
            if (normalized.contains("left")) {
                return "\u5de6\u524d\u536b";
            }
            if (normalized.contains("right")) {
                return "\u53f3\u524d\u536b";
            }
            return "\u4e2d\u573a";
        }
        if (normalized.contains("wing")) {
            if (normalized.contains("left")) {
                return "\u5de6\u8fb9\u950b";
            }
            if (normalized.contains("right")) {
                return "\u53f3\u8fb9\u950b";
            }
            return "\u8fb9\u950b";
        }
        if (normalized.contains("forward") || normalized.contains("attack")
                || normalized.contains("offence") || normalized.contains("offense")
                || normalized.contains("striker")) {
            return "\u524d\u950b";
        }
        return rawPosition;
    }

    private boolean containsChinese(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }
}
