package com.zjj.service;

import java.util.List;
import java.util.Map;

public interface UserService {
	Integer insertData(List<Map<String, Object>> list);

	List<String> getData(Map<String, Object> map);
	
	String queryPassword(String userName);
	
}
