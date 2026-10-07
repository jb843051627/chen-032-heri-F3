package com.fc.v2.controller.admin;

import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.THeriCusBook;
import com.fc.v2.model.auto.THeriCusRow;
import com.fc.v2.model.custom.CusIntakeCmd;
import com.fc.v2.model.custom.CusIntakeReceipt;
import com.fc.v2.model.custom.CusRowTrace;
import com.fc.v2.service.ITHeriCusBookService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报关行整串进门 Controller（whole-string intake：只收整串，无散行增删改入口）。
 *
 * <p>页面上抢先亮的数不算依据：回执、查询都以册上“逐行定去向”那一趟为准。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Api(value = "报关整串核放")
@RestController
@RequestMapping("/heriCusBook")
public class HeriCusBookController extends BaseController {

    @Autowired
    private ITHeriCusBookService heriCusBookService;

    @Log(title = "报关整串进门", action = "intake")
    @ApiOperation(value = "整串进门", notes = "整串进门；同串再进只照面不翻账")
    @PostMapping("/intake")
    @RequiresPermissions("heri:cusBook:intake")
    public AjaxResult intake(@RequestBody CusIntakeCmd cmd) {
        CusIntakeReceipt receipt = heriCusBookService.intake(cmd, currentOperator());
        return retobject(200, receipt);
    }

    @ApiOperation(value = "册子头", notes = "按册号查册子头")
    @GetMapping("/book")
    @RequiresPermissions("heri:cusBook:list")
    public AjaxResult book(@RequestParam String bookNo) {
        THeriCusBook head = heriCusBookService.selectByBookNo(bookNo);
        if (head == null) {
            return error("册号不在册上：" + bookNo);
        }
        return retobject(200, head);
    }

    @ApiOperation(value = "册子行", notes = "行次升序；存量行一条不减")
    @GetMapping("/rows")
    @RequiresPermissions("heri:cusBook:list")
    public AjaxResult rows(@RequestParam String bookNo) {
        return retobject(200, heriCusBookService.listRows(bookNo));
    }

    @ApiOperation(value = "逐行倒查", notes = "随便点一行，看它落在哪档、跟三笔对不对得上")
    @GetMapping("/trace")
    @RequiresPermissions("heri:cusBook:list")
    public AjaxResult trace(@RequestParam String bookNo, @RequestParam Integer rowNo) {
        CusRowTrace t = heriCusBookService.traceRow(bookNo, rowNo);
        if (t == null) {
            return error("第" + rowNo + "行不在册上");
        }
        return retobject(200, t);
    }

    @ApiOperation(value = "三笔对账", notes = "按逐行重算；对不上逐行点名")
    @GetMapping("/audit")
    @RequiresPermissions("heri:cusBook:list")
    public AjaxResult audit(@RequestParam String bookNo) {
        List<String> notes = heriCusBookService.audit(bookNo);
        java.util.Map<String, Object> data = new java.util.HashMap<String, Object>();
        data.put("consistent", notes.isEmpty());
        data.put("notes", notes);
        return retobject(200, data);
    }

    /**
     * 回执查询：三笔按册上逐行重算给数（不以页面抢先亮的为准）；
     * 签+退=进门。空册只给“空册”，没有收讫句。
     */
    @ApiOperation(value = "回执查询", notes = "三笔以逐行重算为准")
    @GetMapping("/receipt")
    @RequiresPermissions("heri:cusBook:list")
    public AjaxResult receipt(@RequestParam String bookNo) {
        THeriCusBook head = heriCusBookService.selectByBookNo(bookNo);
        if (head == null) {
            return error("册号不在册上：" + bookNo);
        }
        List<THeriCusRow> rows = heriCusBookService.listRows(bookNo);
        int signed = 0;
        int back = 0;
        for (THeriCusRow r : rows) {
            if (r.getStatus() != null && r.getStatus() == 1) {
                signed++;
            } else if (r.getStatus() != null && r.getStatus() == 2) {
                back++;
            }
        }
        java.util.Map<String, Object> data = new java.util.HashMap<String, Object>();
        data.put("bookNo", head.getBookNo());
        data.put("bizNo", head.getBizNo());
        data.put("source", head.getSource());
        data.put("declaredRows", head.getDeclareRows());
        data.put("inRows", rows.size());
        data.put("signedRows", signed);
        data.put("backRows", back);
        data.put("balanced", signed + back == rows.size());
        data.put("emptyBook", rows.isEmpty());
        data.put("receivedText", rows.isEmpty()
                ? "空册"
                : ("收讫：进门 " + rows.size() + " 行，签下 " + signed + " 行，退回 " + back + " 行"));
        return retobject(200, data);
    }

    private static String currentOperator() {
        try {
            return ShiroUtils.getLoginName();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
