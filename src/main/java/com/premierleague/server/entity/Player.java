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
        return NATIONALITY_MAP.getOrDefault(nationality, nationality);
    }
}
