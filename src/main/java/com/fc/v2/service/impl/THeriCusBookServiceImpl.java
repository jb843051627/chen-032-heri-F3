package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THeriCusBookMapper;
import com.fc.v2.mapper.auto.THeriCusKindRuleMapper;
import com.fc.v2.mapper.auto.THeriCusRowMapper;
import com.fc.v2.model.auto.THeriCusBook;
import com.fc.v2.model.auto.THeriCusKindRule;
import com.fc.v2.model.auto.THeriCusRow;
import com.fc.v2.model.custom.CusDoorReject;
import com.fc.v2.model.custom.CusIntakeCmd;
import com.fc.v2.model.custom.CusIntakeReceipt;
import com.fc.v2.model.custom.CusRowInput;
import com.fc.v2.model.custom.CusRowTrace;
import com.fc.v2.service.ITHeriCusBookService;
import com.fc.v2.util.SnowflakeIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 报关行整串进门 Service实现。
 *
 * <p>去年那档事的根：第二遍进来把第一遍已签下的行整片冲成待签。这里把路定死：</p>
 * <ul>
 *   <li>只收整串：串头写几行只当几行（空按 150），多出的行计数溢出、另开一串，绝不就地接；</li>
 *   <li>解析只跑一趟：箱型按在册白名单逐行先核，空箱型按“没写”——不合的拦在门外不落册；</li>
 *   <li>门内逐行定去向：申报件数/实签件数缺一不办（不当零），不等就退并写明“少/多 N 件”；</li>
 *   <li>行次册上在本串内排 1..N，落定终身；退了隔天再报还是当初那个号；</li>
 *   <li>同串第二遍只照面：已签行不回待签、不另起记录；续办只补退回行，已签的不惊动；</li>
 *   <li>三笔（进门/签下/退回）同一趟取数，签+退=进门；空册回执只写“空册”，不出收讫。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class THeriCusBookServiceImpl implements ITHeriCusBookService {

    /** 串头那格空着时按 150 行算 */
    public static final int DEFAULT_DECLARED_ROWS = 150;

    // ---- 行状态：0待办 1已签 2退回。已签是终态，任何再进门都不许把它拨回去 ----
    private static final int ROW_PENDING = 0;
    private static final int ROW_SIGNED = 1;
    private static final int ROW_BACK = 2;

    /** 册子状态 0在册 1封住（全签下才封） */
    private static final int BOOK_OPEN = 0;
    private static final int BOOK_SEALED = 1;

    private static final String SOURCE_NA = "未注明";

    /** 每一串一段锁：同串核签排队，异串各锁各的、互不踩 */
    private final ConcurrentHashMap<String, Object> segmentLocks = new ConcurrentHashMap<String, Object>();

    /** 册上发号（册号），C+日期+雪花号，唯一 */
    private static final SnowflakeIdWorker ID_WORKER = new SnowflakeIdWorker(2, 1);

    @Resource
    private THeriCusBookMapper cusBookMapper;
    @Resource
    private THeriCusRowMapper cusRowMapper;
    @Resource
    private THeriCusKindRuleMapper cusKindRuleMapper;

    @Override
    public THeriCusBook selectByBookNo(String bookNo) {
        if (bookNo == null || bookNo.trim().isEmpty()) {
            return null;
        }
        return this.cusBookMapper.selectOne(new QueryWrapper<THeriCusBook>()
                .eq("book_no", bookNo.trim()).eq("del_flag", 0).last("limit 1"));
    }

    @Override
    public THeriCusBook selectByBiz(String bizNo, String source) {
        if (bizNo == null || bizNo.trim().isEmpty()) {
            return null;
        }
        return this.cusBookMapper.selectOne(new QueryWrapper<THeriCusBook>()
                .eq("biz_no", bizNo.trim()).eq("source", normSource(source))
                .eq("del_flag", 0).last("limit 1"));
    }

    @Override
    public CusIntakeReceipt intake(CusIntakeCmd cmd, String operator) {
        if (cmd == null) {
            throw new IllegalArgumentException("只收整串，交单不能为空");
        }
        String bizNo = trim(cmd.getBizNo());
        if (bizNo == null) {
            throw new IllegalArgumentException("串的业务键(总单号/清单号)不能为空，没它认不住是不是同一串");
        }
        String source = normSource(cmd.getSource());
        // 串头：空=150；非空必须整格是行数。原数原样记下，隔年查得回。
        int declared = parseDeclaredRows(cmd.getDeclaredRows());
        List<CusRowInput> sheet = cmd.getRows() == null ? new ArrayList<CusRowInput>() : cmd.getRows();

        String key = bizNo + "||" + source;
        synchronized (lockOf(key)) {
            THeriCusBook head = selectByBiz(bizNo, source);
            if (head == null) {
                return registerFirst(bizNo, source, declared, sheet, cmd.getShipName(), operator);
            }
            // 同串第二遍及以后：照册上的来，只照面/补退回，绝不翻已签的账
            return registerAgain(head, declared, sheet, cmd.getShipName(), operator);
        }
    }

    // ---------------- 头一趟 ----------------

    /** 头一趟进门：空头照立（空册是正经输入）；非空解析一趟、逐行定去向。 */
    private CusIntakeReceipt registerFirst(String bizNo, String source, int declared,
                                          List<CusRowInput> sheet, String shipName, String operator) {
        Date now = new Date();
        String bookNo = "C" + new java.text.SimpleDateFormat("yyyyMMdd").format(now)
                + ID_WORKER.nextId();

        THeriCusBook head = new THeriCusBook();
        head.setBookNo(bookNo);
        head.setBizNo(bizNo);
        head.setSource(source);
        head.setDeclareRows(declared);
        head.setInRows(0);
        head.setSignedRows(0);
        head.setBackRows(0);
        head.setReceivedFlag(0);
        head.setStatus(BOOK_OPEN);
        head.setDelFlag(0);
        head.setCreateBy(operator);
        head.setCreateTime(now);
        try {
            this.cusBookMapper.insert(head);
        } catch (RuntimeException dup) {
            // 并发下两册同键同刻进门：先落的在册，后来的按第二遍走（照面/续办）
            THeriCusBook exist = selectByBiz(bizNo, source);
            if (exist != null) {
                return registerAgain(exist, declared, sheet, shipName, operator);
            }
            throw dup;
        }

        CusIntakeReceipt rc = baseReceipt(head, 1, false, declared, sheet.size());
        if (sheet.isEmpty()) {
            // 空串：册子照立（原报行数仍在），回执只写“空册”，收讫句不出，不从别串拉一行补数
            fillTrip(rc, 0, 0, 0);
            rc.setOverflowRows(0);
            rc.setEmptyBook(true);
            return rc;
        }

        // 解析只跑一趟：先截串（数字之外多的另开一串），再对本串这段逐行核箱型
        List<CusRowInput> slice = sliceOf(sheet, declared);
        int overflow = Math.max(0, sheet.size() - declared);
        Set<String> allowedKinds = activeKinds();
        List<CusDoorReject> doorRejects = new ArrayList<CusDoorReject>();
        List<ParsedRow> parsed = parseOnce(slice, allowedKinds, doorRejects);

        int seq = 0;
        for (ParsedRow p : parsed) {
            seq++;
            THeriCusRow r = new THeriCusRow();
            r.setBookNo(bookNo);
            r.setRowNo(seq); // 行次册上排，交单方没处填
            r.setEntryId(p.entryId);
            r.setDeclareQty(p.declareQty);
            r.setSignedQty(p.signedQty);
            r.setDeviceKind(p.deviceKind);
            r.setShipName(trim(shipName));
            r.setIntakeSeq(1);
            r.setDelFlag(0);
            r.setCreateBy(operator);
            r.setCreateTime(now);
            String diff = qtyDiff(p);
            if (diff == null) {
                r.setStatus(ROW_SIGNED);
            } else {
                r.setStatus(ROW_BACK);
                r.setBackReason(diff);
            }
            this.cusRowMapper.insert(r);
        }

        int signed = countStatus(bookNo, ROW_SIGNED);
        int back = countStatus(bookNo, ROW_BACK);
        int total = signed + back;
        // 三笔同一趟落头表；收讫记只在确有行进门时出（全拦在门外时没东西收讫）；
        // 册子封住得是申报的行全落齐且一行没退——少了就开着等续办。
        finishHead(head, total, signed, back, operator);
        boolean sealed = total > 0 && back == 0 && total == declared;
        head.setReceivedFlag(total > 0 ? 1 : 0);
        head.setStatus(sealed ? BOOK_SEALED : BOOK_OPEN);
        this.cusBookMapper.updateById(head);

        CusIntakeReceipt rc2 = baseReceipt(head, 1, false, declared, sheet.size());
        rc2.setOverflowRows(overflow);
        rc2.setDoorRejects(doorRejects); // 门外明细只此一趟，不重核
        fillTrip(rc2, total, signed, back);
        if (total > 0) {
            rc2.setAcceptedText("收讫：册号" + bookNo + "，进门 " + total
                    + " 行，签下 " + signed + " 行，退回 " + back + " 行");
        }
        return rc2;
    }

    // ---------------- 第二遍及以后（照面 / 续办） ----------------

    /**
     * 同串再进：已签的行只照面，不回待签、不另落；退回行重新报来才续办，
     * 行号认当初那个；没报的退回行原样留着。三笔只算本趟真正定了去向的行。
     */
    private CusIntakeReceipt registerAgain(THeriCusBook head, int declared,
                                          List<CusRowInput> sheet, String shipName, String operator) {
        String bookNo = head.getBookNo();
        int nextSeq = maxIntakeSeq(bookNo) + 1;
        CusIntakeReceipt rc = baseReceipt(head, nextSeq, true, declared, sheet.size());
        if (sheet.isEmpty()) {
            // 空串再进：照面，连收讫句都不出
            rc.setEmptyBook(true);
            return rc;
        }

        Set<String> allowedKinds = activeKinds();

        // 已在册子上的行按报关单号索引：签下的照面，退回的候续
        Map<String, THeriCusRow> onBook = new java.util.HashMap<String, THeriCusRow>();
        for (THeriCusRow r : listRows(bookNo)) {
            if (r.getEntryId() != null) {
                onBook.put(r.getEntryId(), r);
            }
        }
        // 本串能收的行数以头一趟串头为定（隔年不改口）；续办只补到补齐为止，
        // 多出来的行另开一串，不许顺着第二遍的串头扩本串。
        int capacityLeft = head.getDeclareRows() == null ? 0
                : head.getDeclareRows() - onBook.size();
        Set<String> seenInSheet = new HashSet<String>();
        Date now = new Date();
        int nextRowNo = maxRowNo(bookNo);
        int tripIn = 0;
        int tripSigned = 0;
        int tripBack = 0;
        int overflow = 0;

        for (int i = 0; i < sheet.size(); i++) {
            CusRowInput in = sheet.get(i);
            String entryId = trim(in.getEntryId());
            THeriCusRow existed = entryId == null ? null : onBook.get(entryId);
            if (existed != null && existed.getStatus() != null && existed.getStatus() == ROW_SIGNED) {
                // 头一遍签下的：第二遍只照面，账一根毫毛都不动
                continue;
            }
            // 解析一趟：门只对“要办的行”（退回续报行/新行）核；已签行已照面跳过。
            // 注意：新行先看还在不在本串容量内，满了的行归“另开一串”，不进门外明细。
            if (existed == null && capacityLeft <= 0) {
                overflow++;
                continue;
            }
            String door = doorReason(entryId, in.getDeviceKind(), allowedKinds);
            if (door == null && entryId != null && !seenInSheet.add(entryId)) {
                door = "报关单号在本串里重复，认不住落哪一行";
            }
            if (door != null) {
                rc.getDoorRejects().add(new CusDoorReject(i + 1, entryId, trim(in.getDeviceKind()), door));
                continue;
            }

            ParsedRow p = parseOne(entryId, in);
            String diff = qtyDiff(p);
            if (existed != null) {
                // 续办退回行：还是当初那个行号，只更新它自己这一条
                existed.setDeclareQty(p.declareQty);
                existed.setSignedQty(p.signedQty);
                existed.setDeviceKind(p.deviceKind);
                existed.setIntakeSeq(nextSeq);
                existed.setUpdateBy(operator);
                existed.setUpdateTime(now);
                if (diff == null) {
                    existed.setStatus(ROW_SIGNED);
                    existed.setBackReason(null);
                    tripSigned++;
                } else {
                    existed.setStatus(ROW_BACK);
                    existed.setBackReason(diff);
                    tripBack++;
                }
                this.cusRowMapper.updateById(existed);
            } else {
                // 册上没有的新行（中途停了后续补进来的）：往行序后面排，不飘到别的串
                nextRowNo++;
                THeriCusRow r = new THeriCusRow();
                r.setBookNo(bookNo);
                r.setRowNo(nextRowNo);
                r.setEntryId(p.entryId);
                r.setDeclareQty(p.declareQty);
                r.setSignedQty(p.signedQty);
                r.setDeviceKind(p.deviceKind);
                r.setShipName(trim(shipName));
                r.setIntakeSeq(nextSeq);
                r.setDelFlag(0);
                r.setCreateBy(operator);
                r.setCreateTime(now);
                if (diff == null) {
                    r.setStatus(ROW_SIGNED);
                    tripSigned++;
                } else {
                    r.setStatus(ROW_BACK);
                    r.setBackReason(diff);
                    tripBack++;
                }
                this.cusRowMapper.insert(r);
                capacityLeft--; // 新行占一个本串名额；退了也在册上，名额不还
            }
            tripIn++;
        }
        rc.setOverflowRows(overflow);

        int signed = countStatus(bookNo, ROW_SIGNED);
        int back = countStatus(bookNo, ROW_BACK);
        int total = countRows(bookNo);
        Integer declaredRows = head.getDeclareRows();
        boolean sealed = back == 0 && total > 0
                && declaredRows != null && total == declaredRows;
        // 头表三笔按册上逐行重算落定（同一趟数），收讫标记出过就不再改口
        head.setInRows(total);
        head.setSignedRows(signed);
        head.setBackRows(back);
        head.setStatus(sealed ? BOOK_SEALED : BOOK_OPEN);
        head.setUpdateBy(operator);
        head.setUpdateTime(now);
        this.cusBookMapper.updateById(head);

        fillBookCounters(rc, head);
        fillTrip(rc, tripIn, tripSigned, tripBack);
        // 照面/续办不出收讫句
        return rc;
    }

    // ---------------- 解析（只跑一趟） ----------------

    /** 进门解析：截入本串的行逐行核箱型，不合的进 rejects、不进 parsed，两边都只在这一趟出结果。 */
    private List<ParsedRow> parseOnce(List<CusRowInput> slice,
                                      Set<String> allowedKinds, List<CusDoorReject> rejects) {
        List<ParsedRow> parsed = new ArrayList<ParsedRow>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < slice.size(); i++) {
            CusRowInput in = slice.get(i);
            String entryId = trim(in.getEntryId());
            String door = doorReason(entryId, in.getDeviceKind(), allowedKinds);
            if (door == null && entryId != null && !seen.add(entryId)) {
                door = "报关单号在本串里重复，认不住落哪一行";
            }
            if (door != null) {
                rejects.add(new CusDoorReject(i + 1, entryId, trim(in.getDeviceKind()), door));
                continue;
            }
            parsed.add(parseOne(entryId, in));
        }
        return parsed;
    }

    /** 箱型门外核：单号空、箱型空（=没写，不当都可以）、不在在册白名单，都拦。 */
    private String doorReason(String entryId, String deviceKindRaw, Set<String> allowedKinds) {
        if (entryId == null) {
            return "报关单号空着，认不住行，拦在门外";
        }
        String kind = trim(deviceKindRaw);
        if (kind == null) {
            return "箱型没写（空着不当“都可以”），拦在门外";
        }
        if (!allowedKinds.contains(kind)) {
            return "箱型不合：" + kind + "，拦在门外";
        }
        return null;
    }

    private ParsedRow parseOne(String entryId, CusRowInput in) {
        ParsedRow p = new ParsedRow();
        p.entryId = entryId;
        p.deviceKind = trim(in.getDeviceKind());
        p.declareQty = parseQty(in.getDeclareQty());
        p.signedQty = parseQty(in.getSignedQty());
        p.declareRaw = trim(in.getDeclareQty());
        p.signedRaw = trim(in.getSignedQty());
        return p;
    }

    /**
     * 门内逐行定去向：两个件数缺一不办（不当零）；都齐才比，不等就退并写明差在哪。
     * 返回 null=签下；返回字符串=退回原因。
     */
    private String qtyDiff(ParsedRow p) {
        if (p.declareRaw == null && p.signedRaw == null) {
            return "申报件数、实签件数都没写，不办（缺的不当零）";
        }
        if (p.declareRaw == null) {
            return "申报件数没写，不办（缺的不当零）";
        }
        if (p.signedRaw == null) {
            return "册上实签件数没写，不办（缺的不当零）";
        }
        if (p.declareQty == null && p.signedQty == null) {
            return "申报件数、实签件数都不是数（“" + p.declareRaw + "”/“" + p.signedRaw + "”），不办";
        }
        if (p.declareQty == null) {
            return "申报件数不是数：“" + p.declareRaw + "”，不办";
        }
        if (p.signedQty == null) {
            return "册上实签件数不是数：“" + p.signedRaw + "”，不办";
        }
        BigDecimal diff = p.signedQty.subtract(p.declareQty);
        if (diff.signum() == 0) {
            return null;
        }
        String n = diff.abs().stripTrailingZeros().toPlainString();
        return diff.signum() < 0 ? "少 " + n + " 件" : "多 " + n + " 件";
    }

    // ---------------- 倒查 / 对账 ----------------

    @Override
    public List<THeriCusRow> listRows(String bookNo) {
        if (bookNo == null) {
            return new ArrayList<THeriCusRow>();
        }
        return this.cusRowMapper.selectList(new QueryWrapper<THeriCusRow>()
                .eq("book_no", bookNo).eq("del_flag", 0).orderByAsc("row_no"));
    }

    @Override
    public THeriCusRow findRow(String bookNo, Integer rowNo) {
        if (bookNo == null || rowNo == null) {
            return null;
        }
        return this.cusRowMapper.selectOne(new QueryWrapper<THeriCusRow>()
                .eq("book_no", bookNo).eq("row_no", rowNo)
                .eq("del_flag", 0).last("limit 1"));
    }

    @Override
    public CusRowTrace traceRow(String bookNo, Integer rowNo) {
        THeriCusRow r = findRow(bookNo, rowNo);
        if (r == null) {
            return null;
        }
        THeriCusBook head = selectByBookNo(bookNo);
        CusRowTrace t = new CusRowTrace();
        t.setBookNo(bookNo);
        t.setRowNo(r.getRowNo());
        t.setEntryId(r.getEntryId());
        t.setStatus(r.getStatus());
        t.setStatusName(statusName(r.getStatus()));
        t.setIntakeSeq(r.getIntakeSeq());
        t.setBackReason(r.getBackReason());
        t.setTally(tallyOf(r.getStatus()));

        int signed = countStatus(bookNo, ROW_SIGNED);
        int back = countStatus(bookNo, ROW_BACK);
        int total = countRows(bookNo);
        boolean countersMatch = head != null
                && eq(head.getInRows(), total)
                && eq(head.getSignedRows(), signed)
                && eq(head.getBackRows(), back)
                && signed + back == total;
        t.setConsistent(countersMatch);
        if (!countersMatch) {
            t.setMismatchNote("第" + r.getRowNo() + "行(报关单号" + r.getEntryId()
                    + ")落在“" + statusName(r.getStatus()) + "”档；册上三笔为 进门"
                    + (head == null ? "?" : head.getInRows()) + "/签下"
                    + (head == null ? "?" : head.getSignedRows()) + "/退回"
                    + (head == null ? "?" : head.getBackRows())
                    + "，逐行点得 进门" + total + "/签下" + signed + "/退回" + back
                    + "，两头对不上，以逐行这一趟为准");
        }
        return t;
    }

    @Override
    public List<String> audit(String bookNo) {
        List<String> notes = new ArrayList<String>();
        THeriCusBook head = selectByBookNo(bookNo);
        if (head == null) {
            notes.add("册号 " + bookNo + " 不在册上");
            return notes;
        }
        int signed = countStatus(bookNo, ROW_SIGNED);
        int back = countStatus(bookNo, ROW_BACK);
        int total = countRows(bookNo);
        if (!eq(head.getInRows(), total) || !eq(head.getSignedRows(), signed)
                || !eq(head.getBackRows(), back) || signed + back != total) {
            notes.add("三笔对不上：册上写 进门" + head.getInRows() + "/签下"
                    + head.getSignedRows() + "/退回" + head.getBackRows()
                    + "；逐行点得 进门" + total + "/签下" + signed + "/退回" + back
                    + "（签+退应=进门），以逐行这一趟为准。逐行点名：");
            for (THeriCusRow r : listRows(bookNo)) {
                notes.add("第" + r.getRowNo() + "行 报关单号" + r.getEntryId()
                        + " → " + statusName(r.getStatus())
                        + (r.getStatus() != null && r.getStatus() == ROW_BACK
                        && r.getBackReason() != null ? "（" + r.getBackReason() + "）" : ""));
            }
        }
        return notes;
    }

    // ---------------- 零碎 ----------------

    private void finishHead(THeriCusBook head, int total, int signed, int back, String operator) {
        head.setInRows(total);
        head.setSignedRows(signed);
        head.setBackRows(back);
        head.setUpdateBy(operator);
        head.setUpdateTime(new Date());
    }

    private CusIntakeReceipt baseReceipt(THeriCusBook head, int seq, boolean faceOnly,
                                         int declared, int submitted) {
        CusIntakeReceipt rc = new CusIntakeReceipt();
        rc.setBookNo(head.getBookNo());
        rc.setBizNo(head.getBizNo());
        rc.setSource(head.getSource());
        rc.setIntakeSeq(seq);
        rc.setFaceOnly(faceOnly);
        rc.setDeclaredRows(declared);
        rc.setSubmittedRows(submitted);
        fillBookCounters(rc, head);
        return rc;
    }

    private void fillBookCounters(CusIntakeReceipt rc, THeriCusBook head) {
        rc.setBookInRows(head.getInRows() == null ? 0 : head.getInRows());
        rc.setBookSignedRows(head.getSignedRows() == null ? 0 : head.getSignedRows());
        rc.setBookBackRows(head.getBackRows() == null ? 0 : head.getBackRows());
    }

    /** 三笔同趟取数：进门=签下+退回。 */
    private void fillTrip(CusIntakeReceipt rc, int in, int signed, int back) {
        rc.setInRows(in);
        rc.setSignedRows(signed);
        rc.setBackRows(back);
    }

    private Set<String> activeKinds() {
        List<THeriCusKindRule> rules = this.cusKindRuleMapper.selectList(
                new QueryWrapper<THeriCusKindRule>()
                        .eq("status", 0).eq("del_flag", 0));
        Set<String> kinds = new HashSet<String>();
        for (THeriCusKindRule r : rules) {
            String code = trim(r.getKindCode());
            if (code != null) {
                kinds.add(code);
            }
        }
        return kinds;
    }

    /** 串头写几行就只当几行：截前 N 行，多的不就地往下接（由回执溢出计数另开一串）。 */
    private List<CusRowInput> sliceOf(List<CusRowInput> sheet, int declared) {
        if (sheet.size() <= declared) {
            return sheet;
        }
        return new ArrayList<CusRowInput>(sheet.subList(0, declared));
    }

    private int parseDeclaredRows(String raw) {
        String s = trim(raw);
        if (s == null) {
            return DEFAULT_DECLARED_ROWS;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                throw new IllegalArgumentException("串头那格只认行数（空着按 "
                        + DEFAULT_DECLARED_ROWS + "）：“" + raw + "”不是行数");
            }
        }
        long n = Long.parseLong(s);
        if (n <= 0 || n > 100000) {
            throw new IllegalArgumentException("串头行数得在 1..100000 之间：" + n);
        }
        return (int) n;
    }

    private static BigDecimal parseQty(String raw) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(s);
            return v.signum() < 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int countRows(String bookNo) {
        Integer n = this.cusRowMapper.selectCount(new QueryWrapper<THeriCusRow>()
                .eq("book_no", bookNo).eq("del_flag", 0));
        return n == null ? 0 : n;
    }

    private int countStatus(String bookNo, int status) {
        Integer n = this.cusRowMapper.selectCount(new QueryWrapper<THeriCusRow>()
                .eq("book_no", bookNo).eq("status", status).eq("del_flag", 0));
        return n == null ? 0 : n;
    }

    private int maxRowNo(String bookNo) {
        List<THeriCusRow> rows = listRows(bookNo);
        int max = 0;
        for (THeriCusRow r : rows) {
            if (r.getRowNo() != null && r.getRowNo() > max) {
                max = r.getRowNo();
            }
        }
        return max;
    }

    private int maxIntakeSeq(String bookNo) {
        List<THeriCusRow> rows = listRows(bookNo);
        int max = 0;
        for (THeriCusRow r : rows) {
            if (r.getIntakeSeq() != null && r.getIntakeSeq() > max) {
                max = r.getIntakeSeq();
            }
        }
        return max;
    }

    private Object lockOf(String key) {
        Object exist = this.segmentLocks.get(key);
        if (exist != null) {
            return exist;
        }
        Object fresh = new Object();
        Object old = this.segmentLocks.putIfAbsent(key, fresh);
        return old == null ? fresh : old;
    }

    private static String normSource(String s) {
        String t = trim(s);
        return t == null ? SOURCE_NA : t;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean eq(Integer a, int b) {
        return a != null && a == b;
    }

    private static String statusName(Integer st) {
        if (st == null) {
            return "未定档";
        }
        switch (st) {
            case ROW_PENDING:
                return "待办";
            case ROW_SIGNED:
                return "已签";
            case ROW_BACK:
                return "退回";
            default:
                return "未知档(" + st + ")";
        }
    }

    private static String tallyOf(Integer st) {
        if (st == null) {
            return "in";
        }
        if (st == ROW_SIGNED) {
            return "signed";
        }
        if (st == ROW_BACK) {
            return "back";
        }
        return "in";
    }

    /** 门内解析后的一行（件数原文与解析值都留着，缺/错分得清）。 */
    private static final class ParsedRow {
        private String entryId;
        private String deviceKind;
        private BigDecimal declareQty;
        private BigDecimal signedQty;
        private String declareRaw;
        private String signedRaw;
    }
}
