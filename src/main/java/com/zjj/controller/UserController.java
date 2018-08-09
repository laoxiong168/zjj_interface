package com.zjj.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import com.alibaba.fastjson.JSONObject;
import com.zjj.service.AccessCountsService;
import com.zjj.service.UserService;
import com.zjj.utils.Md5Util;
import com.zjj.utils.SpringContextHelper;


@Controller
public class UserController {
	private static Logger logger = Logger.getLogger(UserController.class);
	@Autowired
	private UserService userService;
	@Autowired
	private static long initialAccessCounts = 0;
	private List<String> tokenList = new ArrayList<String>();

	/* 定时任务 */
	static {
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				updateAccessCounts();
			}
		};
		Timer timer = new Timer();
		long delay = 0;
		long intevalPeriod = 1800 * 1000;
		timer.scheduleAtFixedRate(task, delay, intevalPeriod);

	}

	/**
	 * 更新访问数
	 */
	private static void updateAccessCounts() {
		AccessCountsService accessCountsService = (AccessCountsService) SpringContextHelper
				.getBean("accessCountsService");
		Map<String, Object> resultMap = accessCountsService.queryExistData();

		if (resultMap == null) {
			Integer insertCount = accessCountsService.insertAccessCounts(initialAccessCounts);
			logger.info("插入数据：" + initialAccessCounts);
			initialAccessCounts = 0;
		} else {
			logger.info("存在的数据id：" + resultMap.get("id"));
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("id", resultMap.get("id"));
			Long finalCounts = Long.parseLong(resultMap.get("access_counts").toString()) + initialAccessCounts;
			map.put("counts", finalCounts);
			Integer updateCounts = accessCountsService.updateData(map);
			logger.info("数据更新条数：" + finalCounts);
			initialAccessCounts = 0;
		}
	}

	@RequestMapping(value = "/upload")
	public String upload() {
		return "upload";
	}

	@RequestMapping(value = "/queryData")
	public String queryData() {
		return "queryData";
	}
	
	
	

	/**
	 * 有界面的用户查询数据
	 * 
	 * @param name
	 * @param idCode
	 * @param verificationCode
	 * @return
	 */
	@RequestMapping(value = "/query")
	@ResponseBody
	public Map<String, Object> queryInf(String name, String idCode, String verificationCode) {

		String localValidationCode = VerificationCodeController.verlicationCode.toUpperCase();
		Map<String, Object> resultMap = new HashMap<String, Object>();
		Map<String, Object> queryMap = new HashMap<String, Object>();
		if (StringUtils.isBlank(name) || StringUtils.isBlank(idCode) || StringUtils.isBlank(verificationCode)) {
			resultMap.put("isSuccess", false);
			resultMap.put("msg", "请检查输入参数是否正确！");
			return resultMap;
		}

		if (!localValidationCode.equals(verificationCode.toUpperCase())) {
			resultMap.put("isSuccess", false);
			resultMap.put("msg", "验证码不正确！");
			return resultMap;
		}
		String mdtColumn1 = Md5Util.md5(name.replace(" ", "").toUpperCase());
		String mdtColumn2 = Md5Util.md5(idCode.replace(" ", "").toUpperCase());
		queryMap.put("column1", mdtColumn1);
		queryMap.put("column2", mdtColumn2);

		List<String> list = userService.getData(queryMap);
		if (list.size() == 0) {
			resultMap.put("isSuccess", false);
			resultMap.put("msg", "没找到相应数据！");
		} else {
			String result = list.get(list.size() - 1);
			resultMap.put("isSuccess", true);
			resultMap.put("msg", "查询结果：" + result);
			resultMap.put("result", result);
			logger.info("查询结果：" + result);
		}
		return resultMap;
	}

	/**
	 * 后台查询接口查询数据
	 * 
	 * @param request
	 * @return
	 */

	@RequestMapping(value = "/getData")
	@ResponseBody
	public Map<String, Object> getData(HttpServletRequest request) {
		Map<String, Object> resultMap = new HashMap<String, Object>();
		String userName = "";
		try {
			String column1 = request.getParameter("column1");
			String column2 = request.getParameter("column2");
			String token = request.getParameter("token");
			userName = new String(request.getParameter("userName").getBytes("ISO-8859-1"), "UTF-8");
			String timestamp = request.getParameter("timestamp");
			long remoteTimestamp = Long.valueOf(timestamp);
			long localTimestamp = System.currentTimeMillis();
			long intervalTime = Math.abs(localTimestamp - remoteTimestamp) / 1000;
			if (intervalTime > 300) {
				resultMap.put("isSuccess", false);
				resultMap.put("msg", "timestamp错误,请求失效!");
				logger.info("时间戳失效,与当前的时间间隔为:" + intervalTime);
				return resultMap;
			}

			if (StringUtils.isBlank(column1) || StringUtils.isBlank(column2) || StringUtils.isBlank(userName)
					|| StringUtils.isBlank(token)) {
				resultMap.put("isSuccess", false);
				resultMap.put("msg", "参数错误，请检查！");
				return resultMap;
			}
			tokenList.add(token);
			// String passWord = userService.queryPassword(userName);
			String passWord = "123456";
			String totalCharactor = userName + passWord + column1 + column2 + timestamp;
			String localToken = Md5Util.md5(totalCharactor);
			if (!token.equals(localToken)) {
				resultMap.put("isSuccess", false);
				resultMap.put("msg", "非法请求");
				return resultMap;
			}
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("column1", column1);
			map.put("column2", column2);
			// String result = userService.getData(map);
			List<String> resultList = userService.getData(map);
			String result = "";
			logger.info("查询的结果:" + resultList);
			if (resultList.size() != 0) {
				if (resultList.size() > 1) {
					result = resultList.get(resultList.size() - 1);
				} else {
					result = resultList.get(0);
				}
			}
			if (StringUtils.isBlank(result)) {
				resultMap.put("isSuccess", false);
				resultMap.put("msg", "无对应数据");
			} else {
				resultMap.put("isSuccess", true);
				resultMap.put("msg", "查询成功");
				resultMap.put("result", result);
			}
		} catch (Exception e) {
			resultMap.put("isSuccess", false);
			resultMap.put("msg", "查询失败");
			resultMap.put("result", null);
			logger.error("操作人：" + userName + " 获取数据时出错", e);
		}
		initialAccessCounts++;
		logger.info("查询次数：" + initialAccessCounts);
		return resultMap;
	}

	/**
	 * 上传excel
	 * 
	 * @param request
	 * @param timestamp
	 * @param token
	 * @return
	 */
	@RequestMapping(value = "/uploadExcel")
	@ResponseBody
	public Map<String, Object> uploadExcel(HttpServletRequest request, String timestamp, String token) {
		logger.info("开始上传资料");
		String msg = "";
		Map<String, Object> resultMap = new HashMap<String, Object>();
		try {
			String userName = new String(request.getParameter("userName").getBytes("ISO-8859-1"), "UTF-8");
			String passWord = userService.queryPassword(userName);
			String totalCharactor = userName + passWord + timestamp;
			String localToken = Md5Util.md5(totalCharactor);
			if (!token.equals(localToken)) {
				msg = "账号或密码错误！";
				resultMap.put("isSuccess", false);
				resultMap.put("msg", msg);
				return resultMap;
			}
			boolean insertResult = false;
			CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver(
					request.getSession().getServletContext());
			if (request instanceof MultipartHttpServletRequest) {
				// 将request变成多部分request
				MultipartHttpServletRequest multiRequest = (MultipartHttpServletRequest) request;
				Iterator<String> iterator = multiRequest.getFileNames();
				// 检查form中是否有enctype="multipart/form-data"
				if (multipartResolver.isMultipart(request) && iterator.hasNext()) {
					// 获取multiRequest 中所有的文件名
					while (iterator.hasNext()) {
						// 适配名字重复的文件
						List<MultipartFile> fileRows = multiRequest.getFiles(iterator.next().toString());
						if (fileRows != null && fileRows.size() != 0) {
							for (MultipartFile file : fileRows) {
								if (file != null && !file.isEmpty()) {
									// files.add(file);
									String filePath = request.getSession().getServletContext().getRealPath("/")
											+ file.getOriginalFilename();
									// 转存文件
									file.transferTo(new File(filePath));
									File finalFile = new File(filePath);
									insertResult = handleFile(finalFile, userName, filePath);
								}
							}
						}
					}
				}
			}
			if (insertResult) {
				msg = "excel文件内容加密存储成功!";
				resultMap.put("isSuccess", true);
			}
		} catch (FileNotFoundException e) {
			logger.error("没找到文件", e);
		} catch (Exception e) {
			msg = "excel解析出错，请检查excel内容是否规范!";
			resultMap.put("isSuccess", false);
			logger.error("excel解析出错，请检查excel内容是否规范!", e);
		}
		resultMap.put("msg", msg);
		return resultMap;
	}

	private boolean handleFile(File finalFile, String userName, String filePath)
			throws FileNotFoundException, IOException {
		// 解析excel
		Workbook wb = readExcel(filePath);
		if (wb == null) {
			return false;
		}

		// 用来存放表中数据
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		// 获取第一个sheet
		Sheet sheet = wb.getSheetAt(0);
		// 获取最大行数
		int rownum = sheet.getPhysicalNumberOfRows();
		// 获取第一行
		Row row = sheet.getRow(0);
		// 获取最大列数
		int colnum = row.getPhysicalNumberOfCells();
		String cellData = "";
		Integer insertCount = 0;
		for (int i = 1; i < rownum; i++) {
			Map<String, Object> map = new HashMap<String, Object>();
			row = sheet.getRow(i);
			if (row != null) {
				for (int j = 0; j < colnum; j++) {
					cellData = (String) getCellFormatValue(row.getCell(j));
					map.put("column" + j, cellData);
				}
				String id = UUID.randomUUID().toString().replaceAll("-", "");
				map.put("id", id);
				map.put("createUser", userName);
				list.add(map);
				if (list.size() > 1000) {
					insertCount = userService.insertData(list);
					list.clear();
				}
			}
		}

		if (list.size() > 0) {
			insertCount = userService.insertData(list);
		}

		File tempFile = new File(filePath);
		if (tempFile.exists() && tempFile.isFile()) {
			tempFile.delete();
		}

		// 数据写入数据库
		if (insertCount > 0) {
			logger.info("Excel数据导入完成,数据数量：" + rownum);
			return true;
		} else {
			return false;
		}

	}

	// 读取excel
	private Workbook readExcel(String filePath) {
		Workbook wb = null;
		if (filePath == null) {
			return null;
		}
		String extString = filePath.substring(filePath.lastIndexOf("."));
		try {
			InputStream is = new FileInputStream(filePath);
			if (".xls".equals(extString)) {
				return wb = new HSSFWorkbook(is);
			} else if (".xlsx".equals(extString)) {
				return wb = new XSSFWorkbook(is);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return wb;
	}

	public Object getCellFormatValue(Cell cell) {
		Object cellValue = null;
		if (cell != null) {
			// 判断cell类型
			switch (cell.getCellType()) {
			case Cell.CELL_TYPE_NUMERIC: {
				cellValue = String.valueOf(cell.getNumericCellValue());
				break;
			}
			case Cell.CELL_TYPE_FORMULA: {
				// 判断cell是否为日期格式
				if (DateUtil.isCellDateFormatted(cell)) {
					// 转换为日期格式YYYY-mm-dd
					cellValue = cell.getDateCellValue();
				} else {
					// 数字
					cellValue = String.valueOf(cell.getNumericCellValue());
				}
				break;
			}
			case Cell.CELL_TYPE_STRING: {
				cellValue = cell.getRichStringCellValue().getString();
				break;
			}
			default:
				cellValue = "";
			}
		} else {
			cellValue = "";
		}
		return cellValue;
	}

}
