/**
 * Copyright (c) 2012-2014 http://www.eryansky.com
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.eryansky.modules.sys.web;

import com.eryansky.common.exception.ActionException;
import com.eryansky.common.model.Combobox;
import com.eryansky.common.model.Datagrid;
import com.eryansky.common.model.Result;
import com.eryansky.common.orm.Page;
import com.eryansky.common.orm.entity.StatusState;
import com.eryansky.common.orm.hibernate.Parameter;
import com.eryansky.common.utils.DateUtils;
import com.eryansky.common.utils.StringUtils;
import com.eryansky.common.utils.collections.Collections3;
import com.eryansky.common.web.springmvc.SimpleController;
import com.eryansky.common.web.utils.WebUtils;
import com.eryansky.core.security.SecurityUtils;
import com.eryansky.core.security.SessionInfo;
import com.eryansky.core.security.annotation.RequiresUser;
import com.eryansky.core.web.upload.exception.FileNameLengthLimitExceededException;
import com.eryansky.core.web.upload.exception.InvalidExtensionException;
import com.eryansky.modules.disk.entity.File;
import com.eryansky.modules.disk.service.DiskManager;
import com.eryansky.modules.disk.service.FileManager;
import com.eryansky.modules.disk.utils.DiskUtils;
import com.eryansky.modules.sys._enum.VersionLogType;
import com.eryansky.modules.sys.mapper.VersionLog;
import com.eryansky.modules.sys.service.VersionLogService;
import com.eryansky.utils.SelectType;
import com.google.common.collect.Lists;
import org.apache.commons.fileupload.FileUploadBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * @author 尔演&Eryan eryanwcp@gmail.com
 * @date 2015-01-09
 */
@Controller
@RequestMapping(value = "${adminPath}/sys/versionLog")
public class VersionLogController extends SimpleController {

    @Autowired
    private VersionLogService versionLogService;
    @Autowired
    private DiskManager diskManager;
    @Autowired
    private FileManager fileManager;



    @RequestMapping(value = {""})
    public String list() {return "modules/sys/versionLog";}

    @ModelAttribute
    public VersionLog get(@RequestParam(required=false) String id) {
        if (StringUtils.isNotBlank(id)){
            return versionLogService.get(id);
        }else{
            return new VersionLog();
        }
    }
    /**
     * 数据列表 TODO
     * @param request
     * @param startTIme 更新时间 - 起始时间
     * @param endTime 更新时间 - 截止时间
     * @return
     */
    @RequestMapping(value = {"datagrid"})
    @ResponseBody
    public Datagrid<VersionLog> datagrid(VersionLog versionLog,HttpServletRequest request,
                                         @RequestParam(value = "startTime", required = false)Date startTIme,
                                         @RequestParam(value = "endTime", required = false)Date endTime) {
        Page<VersionLog> page = new Page<VersionLog>(request);
        Parameter parameter = new Parameter();
        if(startTIme != null){
            parameter.put("startTime", DateUtils.format(startTIme, DateUtils.DATE_TIME_FORMAT));
        }
        if(endTime != null){
            parameter.put("endTime", DateUtils.format(endTime, DateUtils.DATE_TIME_FORMAT));
        }

        parameter.put("versionName",versionLog.getVersionName());
        parameter.put("remark",versionLog.getRemark());
        parameter.put("versionLogType",versionLog.getVersionLogType());

        page = versionLogService.findPage(page, parameter);

        Datagrid<VersionLog> datagrid = new Datagrid<VersionLog>(page.getTotalCount(), page.getResult());
        return datagrid;
    }

    /**
     * @param versionLog
     * @return
     * @throws Exception
     */
    @RequestMapping(value = { "input" })
    public ModelAndView input(@ModelAttribute("model") VersionLog versionLog) {
        ModelAndView modelAndView = new ModelAndView("modules/sys/versionLog-input");
        File file = null;

        String fileId=versionLog.getFileId();
        if (StringUtils.isNotBlank(fileId)) {
            file = diskManager.getFileById(versionLog.getFileId());
        }
        modelAndView.addObject("file", file);
        modelAndView.addObject("model", versionLog);
        return modelAndView;
    }




    /**
     * 日志类型下拉列表.
     */
    @RequestMapping(value = {"versionLogTypeCombobox"})
    @ResponseBody
    public List<Combobox> versionLogTypeCombobox(String selectType) throws Exception {
        List<Combobox> cList = Lists.newArrayList();
        Combobox titleCombobox = SelectType.combobox(selectType);
        if(titleCombobox != null){
            cList.add(titleCombobox);
        }

        VersionLogType[] lts = VersionLogType.values();
        for (int i = 0; i < lts.length; i++) {
            Combobox combobox = new Combobox();
            combobox.setValue(lts[i].getValue().toString());
            combobox.setText(lts[i].getDescription());
            cList.add(combobox);
        }
        return cList;
    }

    public static final  String FOLDER_VERSIONLOG = "versionLog";
    /**
     * 文件上传
     */
    @RequestMapping(value = { "upload" })
    @ResponseBody
    public static Result upload( @RequestParam(value = "uploadFile", required = false)MultipartFile multipartFile) {
        Result result = null;
        SessionInfo sessionInfo = SecurityUtils.getCurrentSessionInfo();
        Exception exception = null;
        File file = null;
        try {
            file = DiskUtils.saveSystemFile(FOLDER_VERSIONLOG, sessionInfo, multipartFile);
            file.setStatus(StatusState.LOCK.getValue());
            DiskUtils.updateFile(file);
            result = Result.successResult().setObj(file.getId()).setMsg("文件上传成功！");
        } catch (InvalidExtensionException e) {
            exception = e;
            result = Result.errorResult().setMsg(DiskUtils.UPLOAD_FAIL_MSG + e.getMessage());
        } catch (FileUploadBase.FileSizeLimitExceededException e) {
            exception = e;
            result = Result.errorResult().setMsg(DiskUtils.UPLOAD_FAIL_MSG);
        } catch (FileNameLengthLimitExceededException e) {
            exception = e;
            result = Result.errorResult().setMsg(DiskUtils.UPLOAD_FAIL_MSG);
        } catch (ActionException e) {
            exception = e;
            result = Result.errorResult().setMsg(DiskUtils.UPLOAD_FAIL_MSG + e.getMessage());
        } catch (IOException e) {
            exception = e;
            result = Result.errorResult().setMsg(DiskUtils.UPLOAD_FAIL_MSG + e.getMessage());
        } finally {
            if (exception != null) {
                if(file != null){
                    DiskUtils.deleteFile(file.getId());
                }
            }
        }
        return result;

    }

    /**
     * 保存
     * @param versionLog
     * @return
     */
    @RequestMapping(value = { "save" })
    @ResponseBody
    public Result save(@ModelAttribute("model") VersionLog versionLog) {
        Result result;
        if(versionLogService.getByVersionCode(versionLog.getVersionLogType(),versionLog.getVersionCode())!=null){
            result=new Result(Result.WARN, "版本内部编号为[" + versionLog.getVersionCode()
                    + "]已存在,请修正!", "versionCode");
            logger.debug(result.toString());
            return result;
        }
        //更新文件为有效状态 上传的时候为lock状态
        if (StringUtils.isNotBlank(versionLog.getFileId())) {
            File vlFile = diskManager.getFileById(versionLog.getFileId());
            vlFile.setStatus(StatusState.NORMAL.getValue());
            diskManager.updateFile(vlFile);
        }
        versionLogService.save(versionLog);
        result = Result.successResult();
        return result;
    }

    /**
     * 清空数据
     * @return
     */
    @RequestMapping(value = {"remove"})
    @ResponseBody
    public Result remove(@RequestParam(value = "ids", required = false) List<String> ids) {
        Result result = null;
        if(Collections3.isNotEmpty(ids)){
            for(String id:ids){
                versionLogService.delete(id);
            }
        }
        result = Result.successResult();
        return result;
    }

    /**
     * 清空所有数据
     * @return
     */
    @RequestMapping(value = {"removeAll"})
    @ResponseBody
    public Result removeAll(){
        versionLogService.removeAll();
        Result result = Result.successResult();
        return result;
    }



    /**
     * 查看通知
     * @param id 通知ID
     * @return
     * @throws Exception
     */

    @RequestMapping(value = { "view/{id}" })
    public ModelAndView view(@PathVariable String id){
        ModelAndView modelAndView = new ModelAndView("modules/sys/versionLog-view");
        File file = null;
        VersionLog model = versionLogService.get(id);
        if(StringUtils.isNotBlank(model.getFileId())){
            file = diskManager.getFileById(model.getFileId());
        }
        modelAndView.addObject("file", file);
        modelAndView.addObject("model", model);
        return modelAndView;
    }


    /**
     * 附件下载
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    @RequiresUser(required = false)
    @RequestMapping(value = {"downloadApp/{versionLogType}"})
    public ModelAndView downloadApp(HttpServletRequest request,HttpServletResponse response,@PathVariable Integer versionLogType) throws Exception {
        VersionLog versionLog = versionLogService.getLatestVersionLog(versionLogType);
        if(versionLog != null && versionLog.getFileId() != null){
            File file = fileManager.getById(versionLog.getFileId());
            WebUtils.setDownloadableHeader(request, response, file.getName());
            file.getDiskFile();
            java.io.File tempFile = file.getDiskFile();
            FileCopyUtils.copy(new FileInputStream(tempFile), response.getOutputStream());
        }else {
            throw new ActionException("下载文件不存在！");
        }
        return null;
    }
}