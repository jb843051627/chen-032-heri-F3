package com.fc.v2.heri;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.THeriDelBillMapper;
import com.fc.v2.mapper.auto.THeriDelGateMapper;
import com.fc.v2.mapper.auto.THeriDelGradeMapper;
import com.fc.v2.mapper.auto.TSysOperLogMapper;
import com.fc.v2.model.auto.THeriDelGrade;
import com.fc.v2.service.impl.THeriReleaseServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 放货台关口链路测试夹具：H2 内存库(MySQL 兼容) + 真实 MyBatis-Plus mapper，
 * 不启 Spring 容器（避开 Shiro/Redis/Druid 自动装配）。
 *
 * @author fuce
 * @date 2026-10-07
 */
public abstract class ReleaseITSupport {

    protected JdbcDataSource dataSource;
    protected SqlSessionTemplate session;

    protected THeriReleaseServiceImpl releaseService;

    protected THeriDelGradeMapper gradeMapper;
    protected THeriDelBillMapper billMapper;
    protected THeriDelGateMapper gateMapper;
    protected TSysOperLogMapper operLogMapper;

    protected void setUp() throws Exception {
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:mem:relit_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        this.dataSource.setUser("sa");
        runScript(this.dataSource);

        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(THeriDelGradeMapper.class);
        cfg.addMapper(THeriDelBillMapper.class);
        cfg.addMapper(THeriDelGateMapper.class);
        cfg.addMapper(TSysOperLogMapper.class);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(this.dataSource);
        factoryBean.setConfiguration(cfg);
        SqlSessionFactory factory = factoryBean.getObject();

        this.session = new SqlSessionTemplate(factory);
        this.gradeMapper = this.session.getMapper(THeriDelGradeMapper.class);
        this.billMapper = this.session.getMapper(THeriDelBillMapper.class);
        this.gateMapper = this.session.getMapper(THeriDelGateMapper.class);
        this.operLogMapper = this.session.getMapper(TSysOperLogMapper.class);

        this.releaseService = new THeriReleaseServiceImpl();
        ReflectionTestUtils.setField(this.releaseService, "delGradeMapper", this.gradeMapper);
        ReflectionTestUtils.setField(this.releaseService, "delBillMapper", this.billMapper);
        ReflectionTestUtils.setField(this.releaseService, "delGateMapper", this.gateMapper);
        ReflectionTestUtils.setField(this.releaseService, "sysOperLogMapper", this.operLogMapper);

        seedGrades();
    }

    /** 港里判据：直发 0-50 走 3 道；换单 50-200 走 2 道；改提 200-1000 走 3 道；1000 往上没写。 */
    protected void seedGrades() {
        grade(1L, "DG01", "直发档", "直发", "0", "50", 3);
        grade(2L, "DG02", "换单档", "换单", "50", "200", 2);
        grade(3L, "DG03", "改提单档", "改提", "200", "1000", 3);
    }

    protected void grade(Long id, String code, String name, String mode,
                         String min, String max, int gates) {
        THeriDelGrade g = new THeriDelGrade();
        g.setId(id);
        g.setGradeCode(code);
        g.setGradeName(name);
        g.setBizMode(mode);
        g.setQtyMin(new BigDecimal(min));
        g.setQtyMax(max == null ? null : new BigDecimal(max));
        g.setGates(gates);
        g.setPriority(10);
        g.setStatus(0);
        g.setDelFlag(0);
        g.setCreateBy("seed");
        g.setCreateTime(new Date());
        this.gradeMapper.insert(g);
    }

    private static void runScript(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             Reader r = new InputStreamReader(
                     ReleaseITSupport.class.getResourceAsStream("/schema-h2.sql"), "UTF-8")) {
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
