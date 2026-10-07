-- heri 港口集装箱提单与货物放货凭证管理 -- schema (chen-032)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_032

CREATE TABLE IF NOT EXISTS t_heri_adv_base (
  id bigint NOT NULL COMMENT '主键',
  grade_code varchar(32) DEFAULT NULL COMMENT '处置档编号',
  grade_name varchar(64) DEFAULT NULL COMMENT '处置档名称',
  th1_max decimal(14,2) DEFAULT NULL COMMENT '直改档上限(元)',
  th2_max decimal(14,2) DEFAULT NULL COMMENT '换单档上限(元)',
  th3_max decimal(14,2) DEFAULT NULL COMMENT '改提单档上限(元)',
  status int DEFAULT NULL COMMENT '处置档状态 0启用 1撤下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改单处置档案';

CREATE TABLE IF NOT EXISTS t_heri_adv_row (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '条目编号',
  biz_no varchar(64) DEFAULT NULL COMMENT '业务代号',
  amount decimal(14,2) DEFAULT NULL COMMENT '改单金额(元)',
  settle_type varchar(128) DEFAULT NULL COMMENT '处置方式',
  ship_name varchar(128) DEFAULT NULL COMMENT '船名航次',
  stage int DEFAULT NULL COMMENT '当前环节 0..2',
  content varchar(255) DEFAULT NULL COMMENT '内容备注',
  last_action varchar(32) DEFAULT NULL COMMENT '最近一次流转动作',
  status int DEFAULT NULL COMMENT '条目状态 0在办 1已定下 2已封住',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改单处置条目';

CREATE TABLE IF NOT EXISTS t_heri_bl_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '签发批次码',
  row_no int DEFAULT NULL COMMENT '原始行次',
  item_code varchar(64) DEFAULT NULL COMMENT '提单代号',
  qty decimal(14,2) DEFAULT NULL COMMENT '本单箱数',
  device_kind varchar(128) DEFAULT NULL COMMENT '箱型',
  ship_name varchar(128) DEFAULT NULL COMMENT '船名航次',
  status int DEFAULT NULL COMMENT '行状态 0待签 1已签 2已作废',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提单签发行';

CREATE TABLE IF NOT EXISTS t_heri_bl_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(32) DEFAULT NULL COMMENT '规则编号',
  rule_name varchar(64) DEFAULT NULL COMMENT '规则名称',
  th1_max decimal(14,2) DEFAULT NULL COMMENT '本航次直发档上限(件)',
  th2_max decimal(14,2) DEFAULT NULL COMMENT '本航次换单档上限(件)',
  th3_max decimal(14,2) DEFAULT NULL COMMENT '本航次改提单档上限(件)',
  eff_start datetime DEFAULT NULL COMMENT '生效起始时刻',
  eff_end datetime DEFAULT NULL COMMENT '生效截止时刻(不含)',
  priority int DEFAULT NULL COMMENT '优先级(数值越大越优先)',
  status int DEFAULT NULL COMMENT '规则状态 0启用 1撤下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提单签发判定规则';

CREATE TABLE IF NOT EXISTS t_heri_del_row (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '条目编号',
  due_at datetime DEFAULT NULL COMMENT '到期时刻',
  amount decimal(14,2) DEFAULT NULL COMMENT '滞期费(元)',
  notify_type varchar(128) DEFAULT NULL COMMENT '提醒方式',
  ship_name varchar(128) DEFAULT NULL COMMENT '船名航次',
  status int DEFAULT NULL COMMENT '条目状态 0待发 1已发 2已处理',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='放货凭证条目';

CREATE TABLE IF NOT EXISTS t_heri_del_rule (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '凭证代号',
  node_no int DEFAULT NULL COMMENT '当前关口 0..2',
  sign_mode int DEFAULT NULL COMMENT '落签模式 0或签 1齐签',
  need_count int DEFAULT NULL COMMENT '本关口应落人数',
  sign_count int DEFAULT NULL COMMENT '本关口已落人数',
  status int DEFAULT NULL COMMENT '凭证状态 0在办 1已核发 2已封住',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='放货凭证核发判定规则';

CREATE TABLE IF NOT EXISTS t_heri_job (
  id bigint NOT NULL COMMENT '主键',
  job_no varchar(32) DEFAULT NULL COMMENT '作业编号',
  job_name varchar(64) DEFAULT NULL COMMENT '作业名称(船名航次)',
  status int DEFAULT NULL COMMENT '作业状态(0草稿1待审2生效中3已生效4部分生效5回滚中6已回滚)',
  total_rows int DEFAULT NULL COMMENT '提交总行数',
  done_rows int DEFAULT NULL COMMENT '生效成功行数',
  fail_rows int DEFAULT NULL COMMENT '生效失败行数',
  eff_start datetime DEFAULT NULL COMMENT '作业生效起始时刻',
  eff_end datetime DEFAULT NULL COMMENT '作业生效截止时刻(不含)',
  version varchar(32) DEFAULT NULL COMMENT '判据版本',
  row_no int DEFAULT NULL COMMENT '行次(头行固定0)',
  bl_no varchar(64) DEFAULT NULL COMMENT '提单号',
  amount decimal(14,2) DEFAULT NULL COMMENT '金额',
  job_qty int DEFAULT NULL COMMENT '数量',
  version_status int DEFAULT NULL COMMENT '版本状态(1生效中 0未生效)',
  writeback_flag int DEFAULT NULL COMMENT '三处写回掩码(1放货凭据2费用流水4经手留痕 7齐)',
  compensate_flag int DEFAULT NULL COMMENT '补偿流水标记(掩码，同 writeback_flag)',
  compensated int DEFAULT NULL COMMENT '是否已补偿(0否1是)',
  compensate_cnt int DEFAULT NULL COMMENT '补偿累计次数',
  fail_reason varchar(255) DEFAULT NULL COMMENT '失败原因',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_head (job_no, row_no) COMMENT '一个作业号在册上只占一行(头行行次0)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提单签发作业';

CREATE TABLE IF NOT EXISTS t_heri_job_row (
  id bigint NOT NULL COMMENT '主键',
  job_no varchar(32) DEFAULT NULL COMMENT '作业编号',
  row_no int DEFAULT NULL COMMENT '行次(册上排1..N，交单方不填)',
  bl_no varchar(64) DEFAULT NULL COMMENT '提单号',
  amount decimal(14,2) DEFAULT NULL COMMENT '金额',
  job_qty int DEFAULT NULL COMMENT '数量',
  version varchar(32) DEFAULT NULL COMMENT '判据版本',
  version_status int DEFAULT NULL COMMENT '版本状态(1生效中 0未生效)',
  writeback_flag int DEFAULT NULL COMMENT '三处写回掩码(1放货凭据2费用流水4经手留痕 7齐)',
  compensate_flag int DEFAULT NULL COMMENT '补偿流水标记(掩码：已冲回哪几处)',
  compensated int DEFAULT NULL COMMENT '是否已补偿(0否1是)',
  compensate_cnt int DEFAULT NULL COMMENT '补偿累计次数(退回重试遍数)',
  fail_reason varchar(255) DEFAULT NULL COMMENT '失败原因',
  status int DEFAULT NULL COMMENT '行状态(0待签1已签2没签成3已退回)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_row (job_no, row_no) COMMENT '同作业号行次不重，幂等重交不重复落'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签发作业行';

-- 报关核放册子：货代每天整串丢表，一串一头；行次册上排，重复进门只照面不翻账。
CREATE TABLE IF NOT EXISTS t_heri_cus_book (
  id bigint NOT NULL COMMENT '主键',
  book_no varchar(40) DEFAULT NULL COMMENT '册号(一串一号，系统发)',
  biz_no varchar(64) DEFAULT NULL COMMENT '串的业务键(总单号/清单号，同键同来源再进=照面)',
  source varchar(64) DEFAULT NULL COMMENT '来源(船代/平台，只记不判；两家各录各的留两串)',
  declare_rows int DEFAULT NULL COMMENT '串头那格报的行数(空按150)，原样入册',
  in_rows int DEFAULT '0' COMMENT '进门行数(逐行定去向那一趟累计)',
  signed_rows int DEFAULT '0' COMMENT '签下行数',
  back_rows int DEFAULT '0' COMMENT '退回行数',
  received_flag int DEFAULT '0' COMMENT '收讫记是否出过 0否1是(空册/照面/续办不出)',
  status int DEFAULT '0' COMMENT '册子状态 0在册 1封住(全签下)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除(存量行一条不减)',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cus_book_no (book_no) COMMENT '册号唯一，第二遍同号进门只照面',
  UNIQUE KEY uk_cus_book_biz (biz_no, source) COMMENT '同业务键同来源只立一册；船代平台各录各的'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报关核放册子';

CREATE TABLE IF NOT EXISTS t_heri_cus_row (
  id bigint NOT NULL COMMENT '主键',
  book_no varchar(40) DEFAULT NULL COMMENT '册号',
  row_no int DEFAULT NULL COMMENT '行次(册上在本串内排，落定终身不改)',
  entry_id varchar(64) DEFAULT NULL COMMENT '报关单号(续报凭它认行)',
  declare_qty decimal(14,2) DEFAULT NULL COMMENT '申报件数(缺不办，不当零)',
  signed_qty decimal(14,2) DEFAULT NULL COMMENT '册上实签件数(缺不办，不当零)',
  device_kind varchar(32) DEFAULT NULL COMMENT '箱型(空=没写，不当都可以)',
  ship_name varchar(128) DEFAULT NULL COMMENT '船名航次',
  status int DEFAULT NULL COMMENT '行状态 0待办 1已签 2退回(已签终态，不回待办)',
  intake_seq int DEFAULT '1' COMMENT '进门趟次(头趟1，续办递增)',
  back_reason varchar(255) DEFAULT NULL COMMENT '退回原因/差异(如 少2件)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cus_row_no (book_no, row_no) COMMENT '同串行次不重；续办用原号，不另落',
  UNIQUE KEY uk_cus_entry (book_no, entry_id) COMMENT '同串报关单号唯一，重复进门不另起记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报关核放册子行';

CREATE TABLE IF NOT EXISTS t_heri_cus_kind_rule (
  id bigint NOT NULL COMMENT '主键',
  kind_code varchar(32) DEFAULT NULL COMMENT '箱型代码(白名单按此值核)',
  kind_name varchar(64) DEFAULT NULL COMMENT '箱型名称',
  status int DEFAULT '0' COMMENT '规则状态 0启用 1撤下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cus_kind_code (kind_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='箱型校验规则';

CREATE TABLE IF NOT EXISTS t_heri_set_row (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '条目编号',
  due_at datetime DEFAULT NULL COMMENT '到期时刻',
  amount decimal(14,2) DEFAULT NULL COMMENT '差异金额(元)',
  base_id bigint DEFAULT NULL COMMENT '所依据的处置档档案主键',
  grade_code varchar(32) DEFAULT NULL COMMENT '处置档编号(冗余自档案)',
  settle_type varchar(128) DEFAULT NULL COMMENT '核销方式',
  ship_name varchar(128) DEFAULT NULL COMMENT '船名航次',
  status int DEFAULT NULL COMMENT '条目状态 0未核销 1已核销 2已退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单证对齐条目';

-- 初始档案数据（id=0 示例行 / id=1 在用行 / id=2 停用行）
-- 判定档册与处置档册各留三行，供按 id 取档的各环节使用；
-- 档案表缺行会让 insert 直接判 0
-- ⇒ 合法记录会因为查不到档案而保存失败。
INSERT IGNORE INTO t_heri_bl_rule (id, rule_code, rule_name, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time)
VALUES (0, 'NR00', '签发直发档：本航次 500 件以内直发出单（示例行）', 500.00, 2000.00, 5000.00, '2020-01-01 00:00:00', '2099-12-31 23:59:59', 10, 0, 0, 'seed', NOW()),
       (1, 'NR01', '签发直发档：本航次 500 件以内直发出单（在用行）', 500.00, 2000.00, 5000.00, '2020-01-01 00:00:00', '2099-12-31 23:59:59', 10, 0, 0, 'seed', NOW()),
       (2, 'NR02', '签发撤下档：本航次 500 件以内直发出单（停用行）', 500.00, 2000.00, 5000.00, '2020-01-01 00:00:00', '2021-12-31 23:59:59', 10, 1, 0, 'seed', NOW());

INSERT IGNORE INTO t_heri_del_rule (id, bill_no, node_no, sign_mode, need_count, sign_count, status, del_flag, create_by, create_time)
VALUES (0, 'NB00', 0, 0, 1, 0, 0, 0, 'seed', NOW()),
       (1, 'NB01', 1, 1, 2, 0, 0, 0, 'seed', NOW());

INSERT IGNORE INTO t_heri_adv_base (id, grade_code, grade_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'GB00', '处置直改档：改单金额在 500 元以内（示例行）', 500.00, 2000.00, 5000.00, 0, 0, 'seed', NOW()),
       (1, 'GB01', '处置直改档：改单金额在 500 元以内（在用行）', 500.00, 2000.00, 5000.00, 0, 0, 'seed', NOW()),
       (2, 'GB02', '处置复核档：改单金额 500 元至 2000 元（停用行）', 500.00, 2000.00, 5000.00, 1, 0, 'seed', NOW());

-- 箱型白名单：进门解析逐行核，在册且启用(status=0)才算合；箱型格空着=没写，照样不合。
INSERT IGNORE INTO t_heri_cus_kind_rule (id, kind_code, kind_name, status, del_flag, create_by, create_time)
VALUES (1, '20GP', '20 尺普通箱', 0, 0, 'seed', NOW()),
       (2, '40GP', '40 尺普通箱', 0, 0, 'seed', NOW()),
       (3, '40HQ', '40 尺高箱', 0, 0, 'seed', NOW()),
       (4, '45HQ', '45 尺高箱(已撤)', 1, 0, 'seed', NOW());


-- ============================================================================
-- 放货台：一票货过几道关，归哪一档、册上写几道，全照港里判据来。
-- 分档看数量：直发档当场放；换单档先换单；改提单档交上去核。
-- 判据没写到的数量，退回重定，系统不自己挑。
-- ============================================================================

-- 放货分档判据（港里的判据）：数量落在 [qty_min, qty_max) 内归这一档；
-- gates 写死这一档几道关——说三道走三道，说两道走两道。
-- 判据只对立册那一刻负责：立册时几道关誊到单册头上，往后改判据/加关口不追老账。
CREATE TABLE IF NOT EXISTS t_heri_del_grade (
  id bigint NOT NULL COMMENT '主键',
  grade_code varchar(32) DEFAULT NULL COMMENT '分档编号',
  grade_name varchar(64) DEFAULT NULL COMMENT '分档名称(直发档/换单档/改提单档)',
  biz_mode varchar(32) DEFAULT NULL COMMENT '放法 直发=当场放 换单=先换单 改提=交上去核',
  qty_min decimal(14,2) DEFAULT NULL COMMENT '数量下限(含)',
  qty_max decimal(14,2) DEFAULT NULL COMMENT '数量上限(不含，空=不封顶)',
  gates int DEFAULT NULL COMMENT '本档几道关(册上写死)',
  priority int DEFAULT '0' COMMENT '优先级(大数先挑)',
  status int DEFAULT '0' COMMENT '状态 0启用 1撤下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_del_grade_code (grade_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='放货分档判据';

-- 放货单册（一票一册）：归哪一档、几道关、以哪一遍为准，立册时写死在头上。
-- gate_count 是判据的誊本：往后判据加关口，老册子照旧走原来的道。
CREATE TABLE IF NOT EXISTS t_heri_del_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(40) DEFAULT NULL COMMENT '放货单号(册号，一票一号)',
  grade_code varchar(32) DEFAULT NULL COMMENT '分档编号(立册写死)',
  grade_name varchar(64) DEFAULT NULL COMMENT '分档名称(立册写死)',
  biz_mode varchar(32) DEFAULT NULL COMMENT '放法(立册写死)',
  qty decimal(14,2) DEFAULT NULL COMMENT '数量(分档只看它)',
  gate_count int DEFAULT NULL COMMENT '册上几道关(立册誊死，不随后续判据改)',
  round_no int DEFAULT '1' COMMENT '当前走到第几遍(收口重来才递增)',
  final_round_no int DEFAULT NULL COMMENT '最后定住的是哪一遍(以这一遍为准)',
  status int DEFAULT '0' COMMENT '册子状态 0在办 1已封住(关齐即封)',
  cur_gate int DEFAULT '1' COMMENT '当前关次(系统从上往下排，不许手填)',
  rollback_cnt int DEFAULT '0' COMMENT '退过几回',
  sealed_time datetime DEFAULT NULL COMMENT '封口时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_del_bill_no (bill_no) COMMENT '单号唯一，同号再立只照面'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='放货单册';

-- 关口落签流水：append-only，一笔一道痕，作废也留着，谁都不许悄悄抹。
-- 同一关只容一笔有效：非末道悬着时后落笔压前笔(09:40/09:41)；
-- 真同时抢签，先落的算数、后到的作废记账；末道首签即封口，之后全是封口后加签。
CREATE TABLE IF NOT EXISTS t_heri_del_gate (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(40) DEFAULT NULL COMMENT '放货单号',
  round_no int DEFAULT NULL COMMENT '第几遍',
  gate_no int DEFAULT NULL COMMENT '关次(系统按册从上往下排)',
  gate_total int DEFAULT NULL COMMENT '册上共几道(落签那一刻的誊本)',
  signer varchar(64) DEFAULT NULL COMMENT '谁落的笔',
  sign_action varchar(64) DEFAULT NULL COMMENT '落签动作(受理/单证齐全/结关)',
  land_time datetime DEFAULT NULL COMMENT '何时落的',
  land_seq bigint DEFAULT NULL COMMENT '落笔全局次序(同时刻抢签拿它分先后)',
  status int DEFAULT NULL COMMENT '1有效 2作废(后落笔压) 3作废(抢签后到) 4作废(封口后加签) 5作废(关次对不上) 6作废(跨步) 7回退摆回',
  trail_ok int DEFAULT '0' COMMENT '留痕是否已同次落齐 0半成品 1齐(只落一处不算数)',
  void_reason varchar(255) DEFAULT NULL COMMENT '作废/摆回缘由',
  superseded_by bigint DEFAULT NULL COMMENT '被哪一笔压掉',
  del_flag int DEFAULT '0' COMMENT '删除标记 恒0(流水只许追加)',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_del_gate_bill (bill_no, round_no, gate_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='放货关口落签流水';

-- 放货分档判据种子：数量决定档，档决定道；1000 件往上判据没写，立册即退回重定。
INSERT IGNORE INTO t_heri_del_grade (id, grade_code, grade_name, biz_mode, qty_min, qty_max, gates, priority, status, del_flag, create_by, create_time)
VALUES (1, 'DG01', '直发档', '直发', 0.00, 50.00, 3, 10, 0, 0, 'seed', NOW()),
       (2, 'DG02', '换单档', '换单', 50.00, 200.00, 2, 10, 0, 0, 'seed', NOW()),
       (3, 'DG03', '改提单档', '改提', 200.00, 1000.00, 3, 10, 0, 0, 'seed', NOW());
