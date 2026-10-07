package com.fc.v2.heri;

import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.THeriCusBookMapper;
import com.fc.v2.mapper.auto.THeriCusKindRuleMapper;
import com.fc.v2.mapper.auto.THeriCusRowMapper;
import com.fc.v2.service.impl.THeriCusBookServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 报关整串进门链路测试夹具：H2 内存库(MySQL 兼容) + 真实 MyBatis-Plus mapper，
 * 不启 Spring 容器（避开 Shiro/Redis/Druid 自动装配）。
 *
 * @author fuce
 * @date 2026-10-07
 */
public abstract class CusBookITSupport {

    protected JdbcDataSource dataSource;
    protected SqlSessionTemplate session;

    protected THeriCusBookServiceImpl cusBookService;

    protected THeriCusBookMapper bookMapper;
    protected THeriCusRowMapper rowMapper;
    protected THeriCusKindRuleMapper kindRuleMapper;

    protected void setUp() throws Exception {
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:mem:cusit_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        this.dataSource.setUser("sa");
        runScript(this.dataSource);

        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(THeriCusBookMapper.class);
        cfg.addMapper(THeriCusRowMapper.class);
        cfg.addMapper(THeriCusKindRuleMapper.class);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(this.dataSource);
        factoryBean.setConfiguration(cfg);
        SqlSessionFactory factory = factoryBean.getObject();

        this.session = new SqlSessionTemplate(factory);
        this.bookMapper = this.session.getMapper(THeriCusBookMapper.class);
        this.rowMapper = this.session.getMapper(THeriCusRowMapper.class);
        this.kindRuleMapper = this.session.getMapper(THeriCusKindRuleMapper.class);

        this.cusBookService = new THeriCusBookServiceImpl();
        ReflectionTestUtils.setField(this.cusBookService, "cusBookMapper", this.bookMapper);
        ReflectionTestUtils.setField(this.cusBookService, "cusRowMapper", this.rowMapper);
        ReflectionTestUtils.setField(this.cusBookService, "cusKindRuleMapper", this.kindRuleMapper);

        seedKinds();
    }

    /** 箱型白名单：20GP/40GP/40HQ 在用；45HQ 撤下（照样不合）。 */
    protected void seedKinds() {
        kind("20GP", "20 尺普通箱", 0);
        kind("40GP", "40 尺普通箱", 0);
        kind("40HQ", "40 尺高箱", 0);
        kind("45HQ", "45 尺高箱(撤)", 1);
    }

    protected void kind(String code, String name, int status) {
        com.fc.v2.model.auto.THeriCusKindRule r =
                new com.fc.v2.model.auto.THeriCusKindRule();
        r.setKindCode(code);
        r.setKindName(name);
        r.setStatus(status);
        r.setDelFlag(0);
        r.setCreateBy("seed");
        r.setCreateTime(new java.util.Date());
        this.kindRuleMapper.insert(r);
    }

    private static void runScript(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             Reader r = new InputStreamReader(
                     CusBookITSupport.class.getResourceAsStream("/schema-h2.sql"), "UTF-8")) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            for (String sql : sb.toString().split(";")) {
                if (!sql.trim().isEmpty()) {
                    st.execute(sql);
                }
            }
        }
    }
}
