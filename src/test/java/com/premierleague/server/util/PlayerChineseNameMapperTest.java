package com.premierleague.server.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerChineseNameMapperTest {

    @Test
    void mapsBigSixCorePlayersWithSpecialCharacters() {
        assertEquals("厄德高", PlayerChineseNameMapper.map("Martin Ødegaard"));
        assertEquals("鲁本·迪亚斯", PlayerChineseNameMapper.map("Rúben Dias"));
        assertEquals("格瓦迪奥尔", PlayerChineseNameMapper.map("Joško Gvardiol"));
        assertEquals("本坦库尔", PlayerChineseNameMapper.map("Rodrigo Bentancur"));
        assertEquals("帕利尼亚", PlayerChineseNameMapper.map("João Palhinha"));
        assertEquals("德拉古辛", PlayerChineseNameMapper.map("Radu Drăgușin"));
    }

    @Test
    void mapsBigSixCommonFirstTeamPlayers() {
        assertEquals("本·怀特", PlayerChineseNameMapper.map("Ben White"));
        assertEquals("拉亚", PlayerChineseNameMapper.map("David Raya"));
        assertEquals("热苏斯", PlayerChineseNameMapper.map("Gabriel Jesus"));
        assertEquals("哈弗茨", PlayerChineseNameMapper.map("Kai Havertz"));
        assertEquals("维卡里奥", PlayerChineseNameMapper.map("Guglielmo Vicario"));
        assertEquals("波罗", PlayerChineseNameMapper.map("Pedro Porro"));
        assertEquals("斯通斯", PlayerChineseNameMapper.map("John Stones"));
        assertEquals("德布劳内", PlayerChineseNameMapper.map("Kevin De Bruyne"));
        assertEquals("阿马德·迪亚洛", PlayerChineseNameMapper.map("Amad Diallo"));
        assertEquals("齐尔克泽", PlayerChineseNameMapper.map("Joshua Zirkzee"));
    }

    @Test
    void mapsCurrentBigSixBackfillQueuePlayers() {
        assertEquals("埃泽", PlayerChineseNameMapper.map("Eberechi Eze"));
        assertEquals("贝蒂内利", PlayerChineseNameMapper.map("Marcus Bettinelli"));
        assertEquals("马特乌斯·努内斯", PlayerChineseNameMapper.map("Matheus Nunes"));
        assertEquals("奥赖利", PlayerChineseNameMapper.map("Nico O'Reilly"));
        assertEquals("海文", PlayerChineseNameMapper.map("Ayden Heaven"));
        assertEquals("塞特福德", PlayerChineseNameMapper.map("Tommy Setford"));
        assertEquals("拉扬·艾特诺里", PlayerChineseNameMapper.map("Rayan Aït Nouri"));
        assertEquals("德拉古辛", PlayerChineseNameMapper.map("Radu Drăgușin"));
        assertEquals("克里斯托弗·恩昆库", PlayerChineseNameMapper.map("Christopher Nkunku"));
    }
}
