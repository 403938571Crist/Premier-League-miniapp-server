package com.premierleague.server.util;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PlayerChineseNameMapper {

    private static final Map<String, String> PLAYER_ZH = new HashMap<>();

    static {
        addPlayer("Erling Haaland", "哈兰德");
        addPlayer("Mohamed Salah", "萨拉赫");
        addPlayer("Bruno Fernandes", "B费");
        addPlayer("Bukayo Saka", "萨卡");
        addPlayer("Cole Palmer", "帕尔默");
        addPlayer("Phil Foden", "福登");
        addPlayer("Son Heung-Min", "孙兴慜", "Son Heung-min");
        addPlayer("Alexander Isak", "伊萨克");
        addPlayer("Viktor Gyokeres", "约克雷斯");
        addPlayer("Dominic Solanke", "索兰克");
        addPlayer("Kai Havertz", "哈弗茨");
        addPlayer("Gabriel Jesus", "热苏斯");
        addPlayer("Gabriel Martinelli", "马丁内利", "Martinelli");
        addPlayer("Martin Odegaard", "厄德高", "Martin Ødegaard");
        addPlayer("Declan Rice", "赖斯");
        addPlayer("William Saliba", "萨利巴");
        addPlayer("Gabriel Magalhaes", "加布里埃尔", "Gabriel");
        addPlayer("Virgil van Dijk", "范戴克");
        addPlayer("Trent Alexander-Arnold", "阿诺德");
        addPlayer("Luis Diaz", "路易斯·迪亚斯", "Luis Díaz");
        addPlayer("Darwin Nunez", "努涅斯", "Darwin Núñez");
        addPlayer("Kevin De Bruyne", "德布劳内");
        addPlayer("Rodri", "罗德里");
        addPlayer("Bernardo Silva", "贝尔纳多·席尔瓦");
        addPlayer("Ruben Dias", "鲁本·迪亚斯", "Rúben Dias");
        addPlayer("Jeremy Doku", "多库");
        addPlayer("Savinho", "萨维奥", "Sávio");
        addPlayer("Mathys Tel", "特尔");
        addPlayer("Rayan Cherki", "谢尔基");
        addPlayer("Marcus Rashford", "拉什福德");
        addPlayer("Alejandro Garnacho", "加纳乔");
        addPlayer("Rasmus Hojlund", "霍伊伦", "Rasmus Højlund");
        addPlayer("Casemiro", "卡塞米罗");
        addPlayer("Bryan Mbeumo", "姆贝乌莫");
        addPlayer("Nicolas Jackson", "杰克逊");
        addPlayer("Enzo Fernandez", "恩佐·费尔南德斯", "Enzo Fernández");
        addPlayer("Moises Caicedo", "凯塞多", "Moisés Caicedo");
        addPlayer("Joao Pedro", "若昂·佩德罗", "João Pedro");
        addPlayer("Richarlison", "理查利森");
        addPlayer("Dejan Kulusevski", "库卢塞夫斯基");
        addPlayer("James Maddison", "麦迪逊");
        addPlayer("Jurrien Timber", "廷贝尔");
        addPlayer("Ben White", "本·怀特");
        addPlayer("David Raya", "拉亚");
        addPlayer("Leandro Trossard", "特罗萨德");
        addPlayer("Mikel Merino", "梅里诺");
        addPlayer("Riccardo Calafiori", "卡拉菲奥里");
        addPlayer("Kepa Arrizabalaga", "凯帕");
        addPlayer("Myles Lewis-Skelly", "刘易斯-斯凯利");
        addPlayer("Christian Norgaard", "诺尔高", "Christian Nørgaard");
        addPlayer("Cristhian Mosquera", "莫斯克拉");
        addPlayer("Martin Zubimendi", "苏比门迪", "Martín Zubimendi");
        addPlayer("Noni Madueke", "马杜埃凯");
        addPlayer("Piero Hincapie", "因卡皮耶", "Piero Hincapié");
        addPlayer("Abdukodir Khusanov", "胡萨诺夫");
        addPlayer("Gianluigi Donnarumma", "多纳鲁马");
        addPlayer("James Trafford", "特拉福德");
        addPlayer("John Stones", "斯通斯");
        addPlayer("Josko Gvardiol", "格瓦迪奥尔", "Joško Gvardiol");
        addPlayer("Mateo Kovacic", "科瓦契奇", "Mateo Kovačić");
        addPlayer("Nathan Ake", "阿克", "Nathan Aké");
        addPlayer("Omar Marmoush", "马尔穆什");
        addPlayer("Rico Lewis", "里科·刘易斯");
        addPlayer("Tijjani Reijnders", "赖因德斯");
        addPlayer("Amad Diallo", "阿马德·迪亚洛");
        addPlayer("Altay Bayindir", "巴因迪尔", "Altay Bayındır");
        addPlayer("Diogo Dalot", "达洛特");
        addPlayer("Harry Maguire", "马奎尔");
        addPlayer("Joshua Zirkzee", "齐尔克泽");
        addPlayer("Kobbie Mainoo", "梅努");
        addPlayer("Leny Yoro", "约罗");
        addPlayer("Lisandro Martinez", "利桑德罗", "Lisandro Martínez");
        addPlayer("Luke Shaw", "卢克·肖");
        addPlayer("Manuel Ugarte", "乌加特");
        addPlayer("Mason Mount", "芒特");
        addPlayer("Matheus Cunha", "库尼亚");
        addPlayer("Matthijs de Ligt", "德利赫特");
        addPlayer("Noussair Mazraoui", "马兹拉维");
        addPlayer("Patrick Dorgu", "多古");
        addPlayer("Tom Heaton", "希顿");
        addPlayer("Tyrell Malacia", "马拉西亚");
        addPlayer("Benjamin Sesko", "塞斯科", "Benjamin Šeško");
        addPlayer("Antonin Kinsky", "金斯基", "Antonín Kinský");
        addPlayer("Archie Gray", "阿奇·格雷");
        addPlayer("Ben Davies", "本·戴维斯");
        addPlayer("Christian Romero", "罗梅罗", "Cristian Romero");
        addPlayer("Conor Gallagher", "加拉格尔");
        addPlayer("Destiny Udogie", "乌多吉");
        addPlayer("Djed Spence", "斯彭斯");
        addPlayer("Guglielmo Vicario", "维卡里奥");
        addPlayer("Joao Palhinha", "帕利尼亚", "João Palhinha");
        addPlayer("Kevin Danso", "丹索");
        addPlayer("Lucas Bergvall", "贝里瓦尔");
        addPlayer("Micky van de Ven", "范德芬", "Mickey van de Ven");
        addPlayer("Mohammed Kudus", "库杜斯");
        addPlayer("Pape Sarr", "帕普·萨尔");
        addPlayer("Pedro Porro", "波罗");
        addPlayer("Radu Dragusin", "德拉古辛", "Radu Drăgușin");
        addPlayer("Rodrigo Bentancur", "本坦库尔");
        addPlayer("Wilson Odobert", "奥多贝尔");
        addPlayer("Xavi Simons", "哈维·西蒙斯");
        addPlayer("Yves Bissouma", "比苏马");
        addPlayer("Randal Kolo Muani", "科洛·穆阿尼");
        addPlayer("Robert Sanchez", "罗伯特·桑切斯");
        addPlayer("Reece James", "里斯·詹姆斯");
        addPlayer("Levi Colwill", "科尔维尔");
        addPlayer("Wesley Fofana", "福法纳");
        addPlayer("Marc Cucurella", "库库雷利亚");
        addPlayer("Romeo Lavia", "拉维亚");
        addPlayer("Malo Gusto", "古斯托");
        addPlayer("Alisson", "阿利松");
        addPlayer("Ibrahima Konate", "科纳特");
        addPlayer("Andrew Robertson", "罗伯逊");
        addPlayer("Alexis Mac Allister", "麦卡利斯特");
        addPlayer("Dominik Szoboszlai", "索博斯洛伊");
        addPlayer("Ryan Gravenberch", "赫拉芬贝赫");
        addPlayer("Diogo Jota", "若塔");
        addPlayer("Cody Gakpo", "加克波");
        addPlayer("Curtis Jones", "柯蒂斯·琼斯");
        addPlayer("Eberechi Eze", "埃泽");
        addPlayer("Marcus Bettinelli", "贝蒂内利");
        addPlayer("Matheus Nunes", "马特乌斯·努内斯");
        addPlayer("Nico O'Reilly", "奥赖利");
        addPlayer("Rayan Aït Nouri", "拉扬·艾特诺里", "Rayan Ait Nouri");
        addPlayer("Tommy Setford", "塞特福德");
        addPlayer("Ayden Heaven", "海文");
        addPlayer("Diego León", "迭戈·莱昂", "Diego Leon");
        addPlayer("Radu Drăgușin", "德拉古辛", "Radu Dragusin");
        addPlayer("Christopher Nkunku", "克里斯托弗·恩昆库");
        // 近期转会 / 常见缺失
        addPlayer("Jack Grealish", "格拉利什");
        addPlayer("Antoine Semenyo", "塞门约");
        addPlayer("Jadon Sancho", "桑乔");
    }

    private PlayerChineseNameMapper() {
    }

    public static String map(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        String direct = PLAYER_ZH.get(playerName);
        if (direct != null) {
            return direct;
        }

        return PLAYER_ZH.get(normalizeKey(playerName));
    }

    private static void addPlayer(String canonicalName, String chineseName, String... aliases) {
        putName(canonicalName, chineseName);
        for (String alias : aliases) {
            putName(alias, chineseName);
        }
    }

    private static void putName(String name, String chineseName) {
        PLAYER_ZH.put(name, chineseName);
        PLAYER_ZH.put(normalizeKey(name), chineseName);
    }

    private static String normalizeKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('Ø', 'O')
                .replace('ø', 'o')
                .replace('Ł', 'L')
                .replace('ł', 'l')
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .replace('Æ', 'A')
                .replace('æ', 'a')
                .replace('Œ', 'O')
                .replace('œ', 'o')
                .replace('ß', 's');

        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' -]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
