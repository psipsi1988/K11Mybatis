package com.kosmo.k11mybatis;

import java.lang.reflect.Parameter;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import mybatis.MemberVO;
import mybatis.MyBoardDTO;
import mybatis.MybatisDAOImpl;
import mybatis.MybatisMemberImpl;
import mybatis.ParameterDTO;
import oracle.net.aso.p;
import util.EnvFileReader;
import util.PagingUtil;

@Controller
public class MybatisController {
	
	/*
	 sevlet-context.xml에서 생성한 빈을 자동으로 주입받아 Mybatis를
	 사용할 준비를 한다. @Autowired는 타입만 일치하면 자동주입
	 받을 수 있다.
	 */
	
	@Autowired
	private SqlSession sqlSession;
	
	//방명록 리스트
	@RequestMapping("/mybatis/list.do")
	public String list(Model model, HttpServletRequest req) {
				
		//파라미터 저장을 위한 DTO객체 생성
		
		ParameterDTO parameterDTO = new ParameterDTO();
		parameterDTO.setSearchField(req.getParameter("searchField"));
		parameterDTO.setSearchTxt(req.getParameter("searchTxt"));
		System.out.println("검색어:"+ parameterDTO.getSearchTxt());
		
		int totalRecordCount =
				sqlSession.getMapper(MybatisDAOImpl.class).getTotalCount(parameterDTO);
		
		//페이지 처리를 위한 설정값.(Environment 이용해서 외부파일로 써야함)
		int pageSize = Integer.parseInt(EnvFileReader.getValue("SpringBbsInit.properties", 
				"springBoard.pageSize"));
		int blockPage = Integer.parseInt(EnvFileReader.getValue("SpringBbsInit.properties", 
				"springBoard.blockPage"));
		
		//전체 페이지 수 계산
		int totalPage =
				(int)Math.ceil((double)totalRecordCount/pageSize);
		
		//현재페이지에 대한 파라미터 처리 및 시작/끝의 rownum구하기
		int nowPage = req.getParameter("nowPage")==null ? 1:
			Integer.parseInt(req.getParameter("nowPage"));
		int start = (nowPage-1)*pageSize+1;
		int end = nowPage*pageSize;
		
		parameterDTO.setStart(start);
		parameterDTO.setEnd(end);
		
		
		//리스트 페이지에 출력할 게시물 가져오기
		ArrayList<MyBoardDTO> lists =
				sqlSession.getMapper(MybatisDAOImpl.class)
					.listPage(parameterDTO);
		
		//페이지 번호에 대한 처리
		String pagingImg =
				PagingUtil.pagingImg(
						totalRecordCount,
						pageSize, blockPage, nowPage,
						req.getContextPath()+"/mybatis/list.do?");
		model.addAttribute("pagingImg", pagingImg);
		
		//레코드에 대한 가공을 위해 for문으로 반복
		for(MyBoardDTO dto : lists) {
			//내용에 대해 줄바꿈 처리
			String temp =
					dto.getContents().replace("\r\n", "<br/>");
			dto.setContents(temp);
		}
		//model 객체에 저장
		model.addAttribute("lists", lists);
		return "08Mybatis/list";
	}
	
	
	
	
	
	//방명록 글쓰기폼
	@RequestMapping("/mybatis/write.do")
	public String write(Model model, HttpSession session, HttpServletRequest req) {
		
		//글쓰기 페이지로 진입시 세션영역에 데이터가 없다면 로그인 페이지로 이동
		if(session.getAttribute("siteUserInfo")==null) {
			/*
			로그인에 성공할 경우 글쓰기 페이지로 이동하기 위해 돌아갈 경로를 
			아래와 같이 저장함. 
			 */
			model.addAttribute("backUrl", "08Mybatis/write");
			return "redirect:login.do";
		}
		return "08Mybatis/write";
	}
	
	
	
	@RequestMapping("/mybatis/login.do")
	public String login(Model model) {
		return "08Mybatis/login";
	}
	
	
	@RequestMapping("/mybatis/loginAction.do")
	public ModelAndView loginAction(
			HttpServletRequest req, HttpSession session) {
		
		//login메소드를 호출함
		MemberVO vo = 
			sqlSession.getMapper(MybatisMemberImpl.class).login(
				req.getParameter("id"), 
				req.getParameter("pass")
			);
			ModelAndView mv = new ModelAndView();
			if(vo==null) {
				//로그인에 실패한 경우
				mv.addObject("LoginNG", "아이디/패스워드가 틀렸습니다.");
				mv.setViewName("08Mybatis/login");
				return mv;
			}
			else {
				//로그인에 성공한 경우 세션영역에 VO객체를 저장한다. 
				session.setAttribute("siteUserInfo", vo);
			}
			
			String backUrl = req.getParameter("backUrl");
			if(backUrl==null || backUrl.equals("")) {
				//돌아갈 페이지가 없다면 로그인 페이지로 이동한다. 
				mv.setViewName("08Mybatis/login");
			}
			else {
				//지정된 페이지로 이동한다. 
				mv.setViewName(backUrl);
			}
			return mv;
		
		
	}
	
	
	//글쓰기 처리
	@RequestMapping(value="/mybatis/writeAction.do", method=RequestMethod.POST)
	public String writeAction(Model model, 
			HttpServletRequest req, HttpSession session) {
		
		//세션영역에 사용자정보가 있는지 확인
		if(session.getAttribute("siteUserInfo")==null) {
			//로그인이 해제된 상태라면 로그인 페이지로 이동한다. 
			return "redirect:login.do";
		}
		
		//Mybatis 사용
		sqlSession.getMapper(MybatisDAOImpl.class).write(
				req.getParameter("name"), 
				req.getParameter("contents"),
				((MemberVO)session.getAttribute("siteUserInfo")).getId()
		);
		/*
		세션영역에 저장된 MemberVO객체에서 아이디 가져오기
		1. Object타입으로 저장된 VO객체를 가져온다. 
		2. MemberVO 타입으로 형변환한다. 
		3. 형변환된 객체를 통해 getter()를 호출하여 아이디를 얻어온다. 
		
		 */
		//글 작성이 완료되면 리스트로 돌아간다. 
		return "redirect:list.do";
	}
		
	
	//로그아웃
	@RequestMapping("/mybatis/logout.do")
	public String logout(HttpSession session) {
		//세션영역을 비워준다. 
		session.setAttribute("siteUserInfo", null);
		return "redirect:login.do";
	}
	
	
	
	
	@RequestMapping("/mybatis/modify.do")
	public String modify(Model model, HttpServletRequest req, HttpSession session) {
		if(session.getAttribute("siteUserInfo")==null) {
			return "redirect:login.do";
		}
		/*
		여러 개의 폼값을 한 번에 Mapper쪽으로 전달하기 위해 DTO 객체를 사용한다. 
		해당 객체는 Mapper에서 즉시 사용할 수 있다. 
		 */
		
		ParameterDTO parameterDTO = new ParameterDTO(); 
		parameterDTO.setBoard_idx(req.getParameter("idx"));//일련번호
		parameterDTO.setUser_id(
				((MemberVO)session.getAttribute("siteUserInfo")).getId());//사용자 아이디

		//Mybatis 호출시 DTO 객체를 파라미터로 전달
		MyBoardDTO dto = 
				sqlSession.getMapper(MybatisDAOImpl.class).view(parameterDTO);
		
		model.addAttribute("dto", dto);
		return "08Mybatis/modify";
	}
	
	
	//수정처리하기
	@RequestMapping("/mybatis/modifyAction.do")
	public String modifyAction(HttpSession session, MyBoardDTO myBoardDTO) {
		//로그인 확인
		if(session.getAttribute("siteUserInfo")==null) {
			
			//model.addAttribute("backUrl", "08Mybatis/modify");
			return "redirect:login.do";
		}
		
		int applyRow = sqlSession.getMapper(MybatisDAOImpl.class).modify(myBoardDTO);
		
		return "redirect:list.do";
		
	}
	
	//글삭제처리
	@RequestMapping("/mybatis/delete.do")
	public String delete(HttpServletRequest req,  HttpSession session ) {
		//로그인 확인
		if(session.getAttribute("siteUserInfo")==null) {
			
			return "redirect:login.do";
		}
		//Mybatis 사용
		sqlSession.getMapper(MybatisDAOImpl.class).delete(
				req.getParameter("idx"), 
				((MemberVO)session.getAttribute("siteUserInfo")).getId()
				);
		
		return "redirect:list.do";
		
	}
	
	
	
	
}
