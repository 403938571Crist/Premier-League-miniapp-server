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

        // ========================================================
        // 2025/26 全联盟扩充（按俱乐部分组，参考维基/虎扑通译）
        // ========================================================

        // --- 阿森纳 Arsenal（青训除外，主力已在前述）---
        addPlayer("Max Dowman", "道曼");

        // --- 阿斯顿维拉 Aston Villa ---
        addPlayer("Emiliano Martínez", "埃米利亚诺·马丁内斯", "Emiliano Martinez");
        addPlayer("Marco Bizot", "比佐特");
        addPlayer("Ezri Konsa", "孔萨");
        addPlayer("Tyrone Mings", "明斯");
        addPlayer("Pau Torres", "帕乌·托雷斯");
        addPlayer("Matty Cash", "卡什");
        addPlayer("Lucas Digne", "迪涅");
        addPlayer("Ian Maatsen", "马特森");
        addPlayer("Victor Lindelöf", "林德洛夫", "Victor Lindelof", "Victor Nilsson-Lindelöf", "Victor Nilsson-Lindelof");
        addPlayer("Boubacar Kamara", "卡马拉");
        addPlayer("Youri Tielemans", "蒂勒曼斯");
        addPlayer("Amadou Onana", "奥纳纳");
        addPlayer("John McGinn", "麦金");
        addPlayer("Ross Barkley", "巴克利");
        addPlayer("Morgan Rogers", "罗杰斯");
        addPlayer("Emiliano Buendía", "布恩迪亚", "Emiliano Buendia");
        addPlayer("Leon Bailey", "贝利");
        addPlayer("Harvey Elliott", "哈维·埃利奥特");
        addPlayer("Douglas Luiz", "道格拉斯·路易斯");
        addPlayer("Ollie Watkins", "沃特金斯");
        addPlayer("Tammy Abraham", "塔米·亚伯拉罕");
        addPlayer("Lamare Bogarde", "博加德");
        addPlayer("Andrés García", "安德烈斯·加西亚", "Andrés Garcia", "Andres Garcia");
        addPlayer("Mohamed Koné", "穆罕默德·科内", "Mohamed Kone");
        addPlayer("Alysson Edward", "阿利松·爱德华", "Alysson");

        // --- 伯恩茅斯 Bournemouth ---
        addPlayer("Fraser Forster", "弗雷泽·福斯特");
        addPlayer("Christos Mandas", "克里斯托斯·曼达斯");
        addPlayer("Đorđe Petrović", "佩特罗维奇", "Djordje Petrović", "Djordje Petrovic", "Đorđe Petrovic");
        addPlayer("Adam Smith", "亚当·史密斯");
        addPlayer("Marcos Senesi", "塞内西");
        addPlayer("Bafodé Diakité", "迪亚基特", "Bafode Diakite");
        addPlayer("James Hill", "詹姆斯·希尔");
        addPlayer("Veljko Milosavljević", "米洛萨夫列维奇", "Veljko Milosavljevic");
        addPlayer("Adrien Truffert", "特鲁费尔");
        addPlayer("Álex Jiménez", "阿莱克斯·希门尼斯", "Alex Jimenez");
        addPlayer("Julio Soler", "胡里奥·索莱尔");
        addPlayer("Lewis Cook", "刘易斯·库克");
        addPlayer("Tyler Adams", "泰勒·亚当斯");
        addPlayer("Ryan Christie", "瑞安·克里斯蒂");
        addPlayer("Alex Scott", "亚历克斯·斯科特");
        addPlayer("Marcus Tavernier", "塔弗尼尔");
        addPlayer("Justin Kluivert", "贾斯汀·克鲁伊维特");
        addPlayer("David Brooks", "布鲁克斯");
        addPlayer("Amine Adli", "阿德利");
        addPlayer("Ben Doak", "本·多克");
        addPlayer("Eli Kroupi", "克鲁皮", "Eli Junior Kroupi");
        addPlayer("Enes Ünal", "于纳尔", "Enes Unal");
        addPlayer("Evanilson", "埃瓦尼尔松");
        addPlayer("Rayan", "拉扬");

        // --- 伯恩利 Burnley ---
        addPlayer("Václav Hladký", "赫拉德基", "Vaclav Hladky");
        addPlayer("Martin Dúbravka", "杜布拉夫卡", "Martin Dubravka");
        addPlayer("Kyle Walker", "凯尔·沃克");
        addPlayer("Connor Roberts", "康纳·罗伯茨");
        addPlayer("Maxime Estève", "埃斯特韦", "Maxime Esteve");
        addPlayer("Jordan Beyer", "拜尔");
        addPlayer("Hjalmar Ekdal", "埃克达尔");
        addPlayer("Axel Tuanzebe", "图安泽贝");
        addPlayer("Quilindschy Hartman", "哈特曼");
        addPlayer("Bashir Humphreys", "汉弗莱斯");
        addPlayer("Joe Worrall", "沃罗尔");
        addPlayer("Josh Cullen", "卡伦");
        addPlayer("James Ward-Prowse", "沃德-普劳斯");
        addPlayer("Hannibal Mejbri", "汉尼拔", "Hannibal");
        addPlayer("Lesley Ugochukwu", "乌戈丘库");
        addPlayer("Jacob Bruun Larsen", "布鲁恩-拉森");
        addPlayer("Loum Tchaouna", "茨豪纳");
        addPlayer("Mike Trésor", "特雷索", "Mike Tresor");
        addPlayer("Marcus Edwards", "马库斯·爱德华兹");
        addPlayer("Zeki Amdouni", "阿姆杜尼");
        addPlayer("Zian Flemming", "弗莱明");
        addPlayer("Lyle Foster", "莱尔·福斯特");
        addPlayer("Ashley Barnes", "巴恩斯");
        addPlayer("Armando Broja", "布罗亚");
        addPlayer("Jaidon Anthony", "杰登·安东尼");
        addPlayer("Florentino", "弗洛伦蒂诺");
        addPlayer("Lucas Pires", "卢卡斯·皮雷斯");
        addPlayer("Josh Laurent", "劳伦特");

        // --- 切尔西 Chelsea（前述已有 Caicedo/Enzo/Jackson/Cucurella/Colwill 等）---
        addPlayer("Filip Jörgensen", "约根森", "Filip Jorgensen");
        addPlayer("Gaga Slonina", "斯洛尼纳");
        addPlayer("Tosin Adarabioyo", "阿达拉比奥约", "Tosin");
        addPlayer("Trevoh Chalobah", "查洛巴");
        addPlayer("Benoît Badiashile", "巴迪亚希勒", "Benoit Badiashile");
        addPlayer("Jorrel Hato", "哈托");
        addPlayer("Mamadou Sarr", "马马杜·萨尔");
        addPlayer("Josh Acheampong", "阿切庞");
        addPlayer("Andrey Santos", "安德烈·桑托斯");
        addPlayer("Dário Essugo", "埃苏戈", "Dario Essugo");
        addPlayer("Pedro Neto", "佩德罗·内托");
        addPlayer("Mykhailo Mudryk", "穆德里克");
        addPlayer("Estêvão", "埃斯特旺", "Estevao", "Estêvão Willian");
        addPlayer("Marc Guiu", "吉乌");

        // --- 水晶宫 Crystal Palace ---
        addPlayer("Dean Henderson", "迪恩·亨德森");
        addPlayer("Walter Benítez", "贝尼特斯", "Walter Benitez");
        addPlayer("Chris Richards", "理查兹");
        addPlayer("Maxence Lacroix", "拉克鲁瓦");
        addPlayer("Marc Guéhi", "格希", "Marc Guehi");
        addPlayer("Chadi Riad", "里亚德");
        addPlayer("Daniel Muñoz", "穆尼奥斯", "Daniel Munoz");
        addPlayer("Tyrick Mitchell", "蒂里克·米切尔");
        addPlayer("Borna Sosa", "索萨");
        addPlayer("Nathaniel Clyne", "克莱因");
        addPlayer("Adam Wharton", "沃顿");
        addPlayer("Cheick Doucouré", "杜库雷", "Cheick Doucoure");
        addPlayer("Will Hughes", "休斯");
        addPlayer("Daichi Kamada", "镰田大地");
        addPlayer("Jefferson Lerma", "莱尔马");
        addPlayer("Justin Devenny", "迪文尼");
        addPlayer("Ismaïla Sarr", "伊斯梅拉·萨尔", "Ismaila Sarr");
        addPlayer("Jean-Philippe Mateta", "马特塔");
        addPlayer("Eddie Nketiah", "恩凯蒂亚");
        addPlayer("Christantus Uche", "克里斯坦图斯·乌切", "Chrisantus");
        addPlayer("Jørgen Strand Larsen", "斯特兰德-拉森", "Jorgen Strand Larsen");
        addPlayer("Yéremy Pino", "耶雷米·皮诺", "Yeremy Pino", "Yeremy");
        addPlayer("Evann Guessand", "盖桑");
        addPlayer("Jaydee Canvot", "卡沃");

        // --- 埃弗顿 Everton ---
        addPlayer("Jordan Pickford", "皮克福德");
        addPlayer("Mark Travers", "特拉弗斯");
        addPlayer("James Tarkowski", "塔尔科夫斯基");
        addPlayer("Jarrad Branthwaite", "布兰斯韦特");
        addPlayer("Michael Keane", "迈克尔·基恩");
        addPlayer("Vitalii Mykolenko", "米科连科");
        addPlayer("Nathan Patterson", "帕特森");
        addPlayer("Séamus Coleman", "科尔曼", "Seamus Coleman");
        addPlayer("Jake O'Brien", "杰克·奥布莱恩");
        addPlayer("Adam Aznou", "阿兹努");
        addPlayer("James Garner", "加纳");
        addPlayer("Idrissa Gueye", "盖耶");
        addPlayer("Tim Iroegbunam", "伊洛格布纳姆");
        addPlayer("Kiernan Dewsbury-Hall", "杜斯伯里-霍尔");
        addPlayer("Charly Alcaraz", "阿尔卡拉斯");
        addPlayer("Merlin Röhl", "罗尔", "Merlin Rohl");
        addPlayer("Dwight McNeil", "麦克尼尔");
        addPlayer("Iliman Ndiaye", "恩迪亚耶");
        addPlayer("Tyler Dibling", "迪布林");
        addPlayer("Tyrique George", "蒂里克·乔治");
        addPlayer("Beto", "贝托");
        addPlayer("Thierno Barry", "蒂耶诺·巴里");

        // --- 富勒姆 Fulham ---
        addPlayer("Bernd Leno", "莱诺");
        addPlayer("Benjamin Lecomte", "莱孔特");
        addPlayer("Issa Diop", "伊萨·迪奥普");
        addPlayer("Joachim Andersen", "约阿希姆·安德森");
        addPlayer("Calvin Bassey", "巴塞");
        addPlayer("Kenny Tete", "泰特");
        addPlayer("Antonee Robinson", "罗宾逊");
        addPlayer("Timothy Castagne", "卡斯塔涅");
        addPlayer("Ryan Sessegnon", "塞塞尼翁");
        addPlayer("Jorge Cuenca", "昆卡");
        addPlayer("Tom Cairney", "凯尔尼");
        addPlayer("Harrison Reed", "里德");
        addPlayer("Sander Berge", "贝里");
        addPlayer("Saša Lukić", "卢基奇", "Sasa Lukic");
        addPlayer("Alex Iwobi", "伊沃比");
        addPlayer("Harry Wilson", "哈里·威尔逊");
        addPlayer("Emile Smith Rowe", "史密斯·罗");
        addPlayer("Samuel Chukwueze", "楚克乌泽");
        addPlayer("Raúl Jiménez", "劳尔·希门尼斯", "Raul Jimenez");
        addPlayer("Rodrigo Muniz", "穆尼斯");
        addPlayer("Joshua King", "约书亚·金", "Josh King");

        // --- 利兹联 Leeds United ---
        addPlayer("Illan Meslier", "梅利耶");
        addPlayer("Lucas Perri", "卢卡斯·佩里");
        addPlayer("Karl Darlow", "达洛");
        addPlayer("Pascal Struijk", "斯特赖克");
        addPlayer("Joe Rodon", "罗顿");
        addPlayer("Jaka Bijol", "比约尔");
        addPlayer("Sebastiaan Bornauw", "博尔瑙");
        addPlayer("Gabriel Gudmundsson", "古德蒙松");
        addPlayer("Jayden Bogle", "博格尔");
        addPlayer("James Justin", "贾斯汀");
        addPlayer("Ethan Ampadu", "安帕杜");
        addPlayer("Anton Stach", "施塔赫");
        addPlayer("Ilia Gruev", "格鲁耶夫");
        addPlayer("Sean Longstaff", "朗斯塔夫");
        addPlayer("Ao Tanaka", "田中碧");
        addPlayer("Brenden Aaronson", "阿隆森");
        addPlayer("Wilfried Gnonto", "格农托");
        addPlayer("Daniel James", "丹尼尔·詹姆斯");
        addPlayer("Joël Piroe", "皮罗", "Joel Piroe");
        addPlayer("Lukas Nmecha", "恩梅查");
        addPlayer("Noah Okafor", "奥卡福");
        addPlayer("Dominic Calvert-Lewin", "卡尔弗特-勒温");
        addPlayer("Facundo Buonanotte", "博纳诺特");

        // --- 利物浦 Liverpool ---
        addPlayer("Alisson Becker", "阿利松", "Alisson");
        addPlayer("Giorgi Mamardashvili", "马马尔达什维利");
        addPlayer("Freddie Woodman", "伍德曼");
        addPlayer("Joe Gomez", "乔·戈麦斯");
        addPlayer("Andy Robertson", "罗伯逊", "Andrew Robertson");
        addPlayer("Conor Bradley", "布拉德利");
        addPlayer("Milos Kerkez", "凯尔克斯");
        addPlayer("Wataru Endo", "远藤航", "Wataru Endō");
        addPlayer("Stefan Bajcetic", "巴伊切蒂奇");
        addPlayer("Federico Chiesa", "基耶萨");
        addPlayer("Giovanni Leoni", "莱昂尼");
        addPlayer("Calvin Ramsay", "拉姆齐");

        // --- 曼城 Manchester City ---
        addPlayer("Sverre Nypan", "尼潘");
        addPlayer("Nico Gonzalez", "尼科·冈萨雷斯");
        addPlayer("Reigan Heskey", "雷根·赫斯基");

        // --- 曼联 Manchester United ---
        addPlayer("Senne Lammens", "拉门斯");
        addPlayer("Chido Obi", "奇多·奥比", "Chido Obi-Martin");

        // --- 纽卡斯尔 Newcastle ---
        addPlayer("Nick Pope", "波普");
        addPlayer("Aaron Ramsdale", "拉姆斯戴尔");
        addPlayer("John Ruddy", "拉迪");
        addPlayer("Mark Gillespie", "吉莱斯皮");
        addPlayer("Kieran Trippier", "特里皮尔");
        addPlayer("Sven Botman", "博特曼");
        addPlayer("Fabian Schär", "法比安·谢尔", "Fabian Schar");
        addPlayer("Dan Burn", "丹·伯恩");
        addPlayer("Lewis Hall", "刘易斯·霍尔");
        addPlayer("Tino Livramento", "利夫拉门托", "Valentino Livramento");
        addPlayer("Malick Thiaw", "蒂奥");
        addPlayer("Emil Krafth", "克拉夫斯");
        addPlayer("Bruno Guimarães", "布鲁诺·吉马良斯", "Bruno Guimaraes");
        addPlayer("Sandro Tonali", "托纳利");
        addPlayer("Joelinton", "若埃林通");
        addPlayer("Joe Willock", "维洛克");
        addPlayer("Lewis Miley", "麦利");
        addPlayer("Jacob Ramsey", "雅各布·拉姆塞");
        addPlayer("Anthony Gordon", "戈登");
        addPlayer("Anthony Elanga", "埃兰加");
        addPlayer("Jacob Murphy", "雅各布·墨菲");
        addPlayer("Harvey Barnes", "巴恩斯");
        addPlayer("Yoane Wissa", "维萨");
        addPlayer("William Osula", "奥苏拉");
        addPlayer("Nick Woltemade", "沃尔特马德");
        addPlayer("Park Seung-Soo", "朴升洙", "Seung-soo Park");

        // --- 诺丁汉森林 Nottingham Forest ---
        addPlayer("Matz Sels", "塞尔斯");
        addPlayer("Angus Gunn", "冈恩");
        addPlayer("John Victor", "约翰·维克托", "John");
        addPlayer("Stefan Ortega", "斯特凡·奥尔特加", "Stefan Ortega Moreno");
        addPlayer("Murillo", "穆里略");
        addPlayer("Nikola Milenković", "米伦科维奇", "Nikola Milenkovic");
        addPlayer("Willy Boly", "博利");
        addPlayer("Morato", "莫拉托");
        addPlayer("Nicolò Savona", "萨沃纳", "Nicolo Savona");
        addPlayer("Ola Aina", "艾纳");
        addPlayer("Neco Williams", "内科·威廉姆斯");
        addPlayer("Luca Netz", "内茨");
        addPlayer("Eric da Silva Moreira", "达席尔瓦·莫雷拉");
        addPlayer("Ibrahim Sangaré", "桑加雷", "Ibrahim Sangare");
        addPlayer("Nicolás Domínguez", "多明格斯", "Nicolas Dominguez");
        addPlayer("Elliot Anderson", "埃利奥特·安德森");
        addPlayer("Ryan Yates", "耶茨");
        addPlayer("James McAtee", "麦卡蒂");
        addPlayer("Morgan Gibbs-White", "吉布斯-怀特");
        addPlayer("Callum Hudson-Odoi", "哈德森-奥多伊");
        addPlayer("Dan Ndoye", "恩多耶");
        addPlayer("Dilane Bakwa", "巴克瓦");
        addPlayer("Omari Hutchinson", "奥马里·哈钦森");
        addPlayer("Igor Jesus", "伊戈尔·热苏斯");
        addPlayer("Chris Wood", "伍德");
        addPlayer("Taiwo Awoniyi", "阿沃尼伊");
        addPlayer("Lorenzo Lucca", "卢卡");
        addPlayer("Jair Cunha", "雅伊尔·库尼亚", "Jair Paula");

        // --- 桑德兰 Sunderland ---
        addPlayer("Robin Roefs", "鲁夫斯");
        addPlayer("Simon Moore", "西蒙·摩尔");
        addPlayer("Dan Ballard", "巴拉德");
        addPlayer("Trai Hume", "休姆");
        addPlayer("Dennis Cirkin", "西尔金");
        addPlayer("Lutsharel Geertruida", "海尔特勒伊达");
        addPlayer("Reinildo Mandava", "莱尼尔多·曼达瓦");
        addPlayer("Nordi Mukiele", "穆基耶莱");
        addPlayer("Omar Alderete", "阿尔德雷特");
        addPlayer("Granit Xhaka", "扎卡");
        addPlayer("Habib Diarra", "迪亚拉");
        addPlayer("Noah Sadiki", "萨迪基");
        addPlayer("Enzo Le Fée", "勒费", "Enzo Le Fee");
        addPlayer("Chris Rigg", "里格");
        addPlayer("Luke O'Nien", "奥尼恩");
        addPlayer("Romaine Mundle", "蒙德尔");
        addPlayer("Wilson Isidor", "伊西多尔");
        addPlayer("Eliezer Mayenda", "马延达");
        addPlayer("Brian Brobbey", "布罗比");
        addPlayer("Bertrand Traoré", "贝尔特朗·特劳雷", "Bertrand Traore");
        addPlayer("Chemsdine Talbi", "塔尔比");
        addPlayer("Nilson Angulo", "安古洛");
        addPlayer("Abdoullah Ba", "阿卜杜拉·巴");

        // --- 热刺 Tottenham（已有大批主力，仅补遗漏） ---
        addPlayer("Brandon Austin", "布兰登·奥斯汀");
        addPlayer("Pape Matar Sarr", "帕普·马塔尔·萨尔");

        // --- 西汉姆联 West Ham ---
        addPlayer("Łukasz Fabiański", "法比安斯基", "Lukasz Fabianski");
        addPlayer("Alphonse Aréola", "阿雷奥拉", "Alphonse Areola");
        addPlayer("Mads Hermansen", "赫尔曼森");
        addPlayer("Konstantinos Mavropanos", "马夫罗帕诺斯");
        addPlayer("Max Kilman", "基尔曼", "Maximilian Kilman");
        addPlayer("Jean-Clair Todibo", "托迪博");
        addPlayer("Axel Disasi", "迪萨西");
        addPlayer("Aaron Wan-Bissaka", "万-比萨卡");
        addPlayer("Kyle Walker-Peters", "沃克-彼得斯");
        addPlayer("El Hadji Malick Diouf", "迪乌夫", "El Hadji Diouf");
        addPlayer("Oliver Scarles", "斯卡尔斯", "Ollie Scarles");
        addPlayer("Tomáš Souček", "索切克", "Tomas Soucek");
        addPlayer("Lewis Orford", "奥福德");
        addPlayer("Soungoutou Magassa", "马加萨");
        addPlayer("Mateus Fernandes", "马特乌斯·费尔南德斯");
        addPlayer("Jarrod Bowen", "鲍恩");
        addPlayer("Crysencio Summerville", "萨默维尔");
        addPlayer("Adama Traoré", "阿达马·特劳雷", "Adama Traore");
        addPlayer("Callum Wilson", "卡勒姆·威尔逊");
        addPlayer("Valentín Castellanos", "卡斯特利亚诺斯", "Valentin Castellanos");
        addPlayer("Pablo Felipe", "巴勃罗·费利佩", "Pablo");

        // --- 狼队 Wolves ---
        addPlayer("José Sá", "若泽·萨", "Jose Sa");
        addPlayer("Sam Johnstone", "约翰斯通");
        addPlayer("Daniel Bentley", "本特利", "Dan Bentley");
        addPlayer("Yerson Mosquera", "耶尔松·莫斯克拉");
        addPlayer("Toti Gomes", "托蒂·戈麦斯");
        addPlayer("Santiago Bueno", "圣地亚哥·布埃诺");
        addPlayer("Ladislav Krejčí", "克雷伊奇", "Ladislav Krejci");
        addPlayer("Matt Doherty", "多尔蒂");
        addPlayer("Hugo Bueno", "雨果·布埃诺");
        addPlayer("David Møller Wolfe", "沃尔夫", "David Moller Wolfe", "David Wolfe");
        addPlayer("Pedro Lima", "佩德罗·利马");
        addPlayer("Jackson Tchatchoua", "查恰瓦");
        addPlayer("João Gomes", "若昂·戈麦斯", "Joao Gomes");
        addPlayer("André", "安德烈", "Andre");
        addPlayer("Jean-Ricner Bellegarde", "贝勒加德");
        addPlayer("Rodrigo Gomes", "罗德里戈·戈麦斯");
        addPlayer("Mateus Mané", "马特乌斯·马内", "Mateus Mane");
        addPlayer("Enso Gonzalez", "恩索·冈萨雷斯");
        addPlayer("Angel Gomes", "安赫尔·戈麦斯");
        addPlayer("Adam Armstrong", "阿姆斯特朗");
        addPlayer("Tolu Arokodare", "阿罗科达雷", "Toluwalas Arokodare");

        // --- 布伦特福德 Brentford ---
        addPlayer("Caoimhín Kelleher", "凯莱赫", "Caoimhin Kelleher");
        addPlayer("Hákon Valdimarsson", "瓦尔迪马松", "Hakon Valdimarsson");
        addPlayer("Nathan Collins", "柯林斯");
        addPlayer("Ethan Pinnock", "平诺克");
        addPlayer("Sepp van den Berg", "范登伯格");
        addPlayer("Kristoffer Ajer", "阿耶尔");
        addPlayer("Aaron Hickey", "希基");
        addPlayer("Rico Henry", "里科·亨利");
        addPlayer("Michael Kayode", "卡约德");
        addPlayer("Mathias Jensen", "马蒂亚斯·延森");
        addPlayer("Vitaly Janelt", "亚内尔特");
        addPlayer("Yehor Yarmoliuk", "亚尔莫柳克");
        addPlayer("Jordan Henderson", "亨德森");
        addPlayer("Antoni Milambo", "米兰博");
        addPlayer("Josh Dasilva", "达席尔瓦");
        addPlayer("Fábio Carvalho", "卡瓦略", "Fabio Carvalho");
        addPlayer("Mikkel Damsgaard", "达姆斯高");
        addPlayer("Kevin Schade", "沙德");
        addPlayer("Reiss Nelson", "莱斯·内尔森");
        addPlayer("Igor Thiago", "伊戈尔·蒂亚戈");
        addPlayer("Dango Ouattara", "瓦塔拉");
        addPlayer("Keane Lewis-Potter", "刘易斯-波特");

        // --- 布莱顿 Brighton ---
        addPlayer("Bart Verbruggen", "维尔布鲁亨");
        addPlayer("Jason Steele", "斯蒂尔");
        addPlayer("Lewis Dunk", "邓克");
        addPlayer("Adam Webster", "韦伯斯特");
        addPlayer("Joël Veltman", "维尔特曼", "Joel Veltman");
        addPlayer("Jan Paul van Hecke", "范赫克");
        addPlayer("Igor Julio", "伊戈尔");
        addPlayer("Olivier Boscagli", "博斯卡利");
        addPlayer("Maxim De Cuyper", "德赖佩");
        addPlayer("Ferdi Kadioglu", "卡迪奥卢");
        addPlayer("Carlos Baleba", "巴莱巴");
        addPlayer("Mats Wieffer", "维费尔");
        addPlayer("Yasin Ayari", "阿亚里");
        addPlayer("Matt O'Riley", "马特·奥赖利");
        addPlayer("Jack Hinshelwood", "欣谢尔伍德");
        addPlayer("Pascal Groß", "格罗斯", "Pascal Gross");
        addPlayer("James Milner", "米尔纳");
        addPlayer("Diego Gómez", "迭戈·戈麦斯", "Diego Gomez");
        addPlayer("Solly March", "马奇");
        addPlayer("Kaoru Mitoma", "三笘薰");
        addPlayer("Yankuba Minteh", "明特");
        addPlayer("Georginio Rutter", "鲁特尔");
        addPlayer("Danny Welbeck", "韦尔贝克");
        addPlayer("Stefanos Tzimas", "齐马斯");
        addPlayer("Charalampos Kostoulas", "科斯图拉斯");
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
