package com.deepoove.dubbo.provider.springboot.provider;

import com.alibaba.dubbo.config.annotation.Service;
import com.deepoove.swagger.dubbo.example.api.pojo.User;
import com.deepoove.swagger.dubbo.example.api.service.UserService;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

	static User user = new User();
	static List<User> list = new ArrayList<User>();

	static {
		user.setId("Sayi");
		user.setName("卅一");
		user.setSite("http://www.deepoove.com");
		list.add(user);
	}

	@Override
	public List<User> query(String phone) {
		System.out.println("查询用户 byPhone: " + phone);
		return list;
	}

	@Override
	public List<User> query(int areaCode) {
		System.out.println("查询用户 byAreaCode: " + areaCode);
		return list;
	}

	@Override
	public User get(String id) {
		System.out.println("获取用户："+id);
		return user;
	}

	@Override
	public void save(User user) {
		System.out.println("成功保存: " + user.toString());
	}

	@Override
	public User update(User user, String operator) {
		System.out.println("更新用户："+user + "\n" + "操作人：" + operator);
		return null;
	}

	@Override
	public void delete(String id) {
		System.out.println("删除用户："+id);
	}

	@Override
	public int compare(User src, User dest) {
		System.out.println("src: "+src);
		System.out.println("dest: "+dest);
		return 0;
	}

}
