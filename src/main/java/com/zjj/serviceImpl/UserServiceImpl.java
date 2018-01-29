package com.zjj.serviceImpl;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zjj.dao.UserDao;
import com.zjj.service.UserService;

@Service("userService")
public class UserServiceImpl implements UserService {
	@Autowired
	private UserDao userDao;

	public Integer insertData(List<Map<String, Object>> list) {
		return userDao.insertDate(list);
	}

	public List<String> getData(Map<String, Object> map) {
		return userDao.getData(map);
	}

	public String queryPassword(String userName) {
		return userDao.queryPassword(userName);
	}

}
