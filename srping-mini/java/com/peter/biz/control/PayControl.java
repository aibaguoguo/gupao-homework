package com.peter.biz.control;

import com.peter.annotation.PTAutowired;
import com.peter.annotation.PTContorller;
import com.peter.annotation.PTRequestMapping;
import com.peter.annotation.PTRequestParam;
import com.peter.biz.beans.Peter;
import com.peter.biz.beans.pay.Ipay;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@PTContorller
public class PayControl {
    @PTAutowired
    private Ipay ipay;
    @PTAutowired
    private Peter peter;

    @PTRequestMapping("/showName")
    public void showName(HttpServletRequest req, @PTRequestParam("name")String name, HttpServletResponse resp) {
        try {
            resp.getWriter().write("hello PTSpring \n\t");
            resp.getWriter().write("name :" + name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @PTRequestMapping("/add")
    public void sub( @PTRequestParam("a") int a, @PTRequestParam("b")int b, HttpServletResponse resp) {
        try {
            resp.getWriter().write("hello PTSpring \n\t");
            resp.getWriter().write("a="+a+",b="+b+";a+b="+(a+b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @PTRequestMapping("/hi")
    public String show(@PTRequestParam("value") String hello){
        return hello;
    }
}
