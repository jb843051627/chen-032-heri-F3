package com.fc.v2.heri;

import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.THeriBlRuleMapper;
import com.fc.v2.mapper.auto.THeriDelRowMapper;
import com.fc.v2.mapper.auto.THeriDelRuleMapper;
import com.fc.v2.mapper.auto.THeriDelSignMapper;
import com.fc.v2.mapper.auto.TSysOperLogMapper;
import com.fc.v2.model.auto.THeriBlRule;
import com.fc.v2.service.impl.THeriBlRuleServiceImpl;
import com.fc.v2.service.impl.THeriDelRuleServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 放货台链路测试夹具：H2 内存库(MySQL 兼容) + 真实 MyBatis-Plus mapper，
 * 不启 Spring 容器（避开 Shiro/Redis/Druid 自动装配）。
 *
 * <p>与签发作业夹具不同：这里显式挂上 DataSourceTransactionManager——
 * 关口那一步动两边账本，签认和留痕必须同一事务落，不能只落一处。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
public abstract class DelRuleITSupport {

    protected JdbcDataSource dataSource;
    protected SqlSessionTemplate session;

    protected THeriDelRuleServiceImpl delRuleService;
    protected THeriBlRuleServiceImpl blRuleService;

    protected THeriDelRuleMapper delRuleMapper;
    protected THeriDelSignMapper delSignMapper;
    protected THeriDelRowMapper delRowMapper;
    protected TSysOperLogMapper operLogMapper;
    protected THeriBlRuleMapper blRuleMapper;

    protected void setUp() throws Exception {
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:mem:delit_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        this.dataSource.setUser("sa");
        runScript(this.dataSource);

        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(THeriDelRuleMapper.class);
        cfg.addMapper(THeriDelSignMapper.class);
        cfg.addMapper(THeriDelRowMapper.class);
        cfg.addMapper(TSysOperLogMapper.class);
        cfg.addMapper(THeriBlRuleMapper.class);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(this.dataSource);
        factoryBean.setConfiguration(cfg);
        SqlSessionFactory factory = factoryBean.getObject();

        this.session = new SqlSessionTemplate(factory);
        this.delRuleMapper = this.session.getMapper(THeriDelRuleMapper.class);
        this.delSignMapper = this.session.getMapper(THeriDelSignMapper.class);
        this.delRowMapper = this.session.getMapper(THeriDelRowMapper.class);
        this.operLogMapper = this.session.getMapper(TSysOperLogMapper.class);
        this.blRuleMapper = this.session.getMapper(THeriBlRuleMapper.class);

        this.blRuleService = new THeriBlRuleServiceImpl();
        ReflectionTestUtils.setField(this.blRuleService, "heriBlRuleMapper", this.blRuleMapper);

        this.delRuleService = new THeriDelRuleServiceImpl();
        ReflectionTestUtils.setField(this.delRuleService, "heriDelRuleMapper", this.delRuleMapper);
        ReflectionTestUtils.setField(this.delRuleService, "heriDelSignMapper", this.delSignMapper);
        ReflectionTestUtils.setField(this.delRuleService, "heriDelRowMapper", this.delRowMapper);
        ReflectionTestUtils.setField(this.delRuleService, "sysOperLogMapper", this.operLogMapper);
        ReflectionTestUtils.setField(this.delRuleService, "heriBlRuleService", this.blRuleService);
        ReflectionTestUtils.setField(this.delRuleService, "transactionManager",
                new DataSourceTransactionManager(this.dataSource));

        seedBlRule();
    }

    /** 港里判据（在用行）：本航次 500 件以内直发、2000 以内换单、5000 以内改提单 */
    protected void seedBlRule() {
        THeriBlRule r = new THeriBlRule();
        r.setRuleCode("NR01");
        r.setRuleName("签发直发档：本航次 500 件以内直发出单（在用行）");
        r.setTh1Max(new java.math.BigDecimal("500"));
        r.setTh2Max(new java.math.BigDecimal("2000"));
        r.setTh3Max(new java.math.BigDecimal("5000"));
        r.setEffStart(new Date(1262275200000L));  // 2010-01-01
        r.setEffEnd(new Date(4102444800000L));    // 2100-01-01
        r.setPriority(10);
        r.setStatus(0);
        r.setDelFlag(0);
        r.setCreateBy("seed");
        r.setCreateTime(new Date());
        this.blRuleMapper.insert(r);
    }

    /** 册上时刻：记录里的 09:12 那一格 */
    protected static Date at(String hhmm) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-10-07 " + hhmm);
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void runScript(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             Reader r = new InputStreamReader(
                     DelRuleITSupport.class.getResourceAsStream("/schema-h2.sql"), "UTF-8")) {
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
