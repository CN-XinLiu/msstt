package com.lx.msstt.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.msstt.common.ErrorCode;
import com.lx.msstt.constant.CommonConstant;
import com.lx.msstt.exception.BusinessException;
import com.lx.msstt.exception.ThrowUtils;
import com.lx.msstt.mapper.QuestionBankQuestionMapper;
import com.lx.msstt.model.dto.questionbankquestion.QuestionBankQuestionQueryRequest;
import com.lx.msstt.model.entity.Question;
import com.lx.msstt.model.entity.QuestionBank;
import com.lx.msstt.model.entity.QuestionBankQuestion;
import com.lx.msstt.model.entity.User;
import com.lx.msstt.model.vo.QuestionBankQuestionVO;
import com.lx.msstt.model.vo.UserVO;
import com.lx.msstt.service.QuestionBankQuestionService;
import com.lx.msstt.service.QuestionBankService;
import com.lx.msstt.service.QuestionService;
import com.lx.msstt.service.UserService;
import com.lx.msstt.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 题库题目关联表服务实现
 *
 * @author lx
 */
@Service
@Slf4j
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion> implements QuestionBankQuestionService {

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    @Lazy
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add) {
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR);
        // 创建数据时，参数不能为空
        Long questionBankId = questionBankQuestion.getQuestionBankId();
        if (questionBankId != null) {
            QuestionBank questionBank = questionBankService.getById(questionBankId);
            ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库不存在");
        }
        Long questionId = questionBankQuestion.getQuestionId();
        if (questionId != null) {
            Question question = questionService.getById(questionId);
            ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
    }

    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest) {
        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        if (questionBankQuestionQueryRequest == null) {
            return queryWrapper;
        }
        // todo 从对象中取值
        Long id = questionBankQuestionQueryRequest.getId();
        Long notId = questionBankQuestionQueryRequest.getNotId();
        Long questionId = questionBankQuestionQueryRequest.getQuestionId();
        Long questionBankId = questionBankQuestionQueryRequest.getQuestionBankId();
        String sortField = questionBankQuestionQueryRequest.getSortField();
        String sortOrder = questionBankQuestionQueryRequest.getSortOrder();
        Long userId = questionBankQuestionQueryRequest.getUserId();
        // todo 补充需要的查询条件
        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionBankId), "questionBankId", questionBankId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题库题目关联表封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    @Override
    public QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request) {
        // 对象转封装类
        QuestionBankQuestionVO questionBankQuestionVO = QuestionBankQuestionVO.objToVo(questionBankQuestion);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = questionBankQuestion.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionBankQuestionVO.setUser(userVO);
        // 2. 已登录，获取用户点赞、收藏状态
//        long questionBankQuestionId = questionBankQuestion.getId();
//        User loginUser = userService.getLoginUserPermitNull(request);
//        if (loginUser != null) {
//            // 获取点赞
//            QueryWrapper<QuestionBankQuestionThumb> questionBankQuestionThumbQueryWrapper = new QueryWrapper<>();
//            questionBankQuestionThumbQueryWrapper.in("questionBankQuestionId", questionBankQuestionId);
//            questionBankQuestionThumbQueryWrapper.eq("userId", loginUser.getId());
//            QuestionBankQuestionThumb questionBankQuestionThumb = questionBankQuestionThumbMapper.selectOne(questionBankQuestionThumbQueryWrapper);
//            questionBankQuestionVO.setHasThumb(questionBankQuestionThumb != null);
//            // 获取收藏
//            QueryWrapper<QuestionBankQuestionFavour> questionBankQuestionFavourQueryWrapper = new QueryWrapper<>();
//            questionBankQuestionFavourQueryWrapper.in("questionBankQuestionId", questionBankQuestionId);
//            questionBankQuestionFavourQueryWrapper.eq("userId", loginUser.getId());
//            QuestionBankQuestionFavour questionBankQuestionFavour = questionBankQuestionFavourMapper.selectOne(questionBankQuestionFavourQueryWrapper);
//            questionBankQuestionVO.setHasFavour(questionBankQuestionFavour != null);
//        }
        // endregion

        return questionBankQuestionVO;
    }

    /**
     * 分页获取题库题目关联表封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request) {
        List<QuestionBankQuestion> questionBankQuestionList = questionBankQuestionPage.getRecords();
        Page<QuestionBankQuestionVO> questionBankQuestionVOPage = new Page<>(questionBankQuestionPage.getCurrent(), questionBankQuestionPage.getSize(), questionBankQuestionPage.getTotal());
        if (CollUtil.isEmpty(questionBankQuestionList)) {
            return questionBankQuestionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionBankQuestionVO> questionBankQuestionVOList = questionBankQuestionList.stream().map(questionBankQuestion -> {
            return QuestionBankQuestionVO.objToVo(questionBankQuestion);
        }).collect(Collectors.toList());

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionBankQuestionList.stream().map(QuestionBankQuestion::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 已登录，获取用户点赞、收藏状态
//        Map<Long, Boolean> questionBankQuestionIdHasThumbMap = new HashMap<>();
//        Map<Long, Boolean> questionBankQuestionIdHasFavourMap = new HashMap<>();
//        User loginUser = userService.getLoginUserPermitNull(request);
//        if (loginUser != null) {
//            Set<Long> questionBankQuestionIdSet = questionBankQuestionList.stream().map(QuestionBankQuestion::getId).collect(Collectors.toSet());
//            loginUser = userService.getLoginUser(request);
//            // 获取点赞
//            QueryWrapper<QuestionBankQuestionThumb> questionBankQuestionThumbQueryWrapper = new QueryWrapper<>();
//            questionBankQuestionThumbQueryWrapper.in("questionBankQuestionId", questionBankQuestionIdSet);
//            questionBankQuestionThumbQueryWrapper.eq("userId", loginUser.getId());
//            List<QuestionBankQuestionThumb> questionBankQuestionQuestionBankQuestionThumbList = questionBankQuestionThumbMapper.selectList(questionBankQuestionThumbQueryWrapper);
//            questionBankQuestionQuestionBankQuestionThumbList.forEach(questionBankQuestionQuestionBankQuestionThumb -> questionBankQuestionIdHasThumbMap.put(questionBankQuestionQuestionBankQuestionThumb.getQuestionBankQuestionId(), true));
//            // 获取收藏
//            QueryWrapper<QuestionBankQuestionFavour> questionBankQuestionFavourQueryWrapper = new QueryWrapper<>();
//            questionBankQuestionFavourQueryWrapper.in("questionBankQuestionId", questionBankQuestionIdSet);
//            questionBankQuestionFavourQueryWrapper.eq("userId", loginUser.getId());
//            List<QuestionBankQuestionFavour> questionBankQuestionFavourList = questionBankQuestionFavourMapper.selectList(questionBankQuestionFavourQueryWrapper);
//            questionBankQuestionFavourList.forEach(questionBankQuestionFavour -> questionBankQuestionIdHasFavourMap.put(questionBankQuestionFavour.getQuestionBankQuestionId(), true));
//        }
        // 填充信息
        questionBankQuestionVOList.forEach(questionBankQuestionVO -> {
            Long userId = questionBankQuestionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionBankQuestionVO.setUser(userService.getUserVO(user));
//            questionBankQuestionVO.setHasThumb(questionBankQuestionIdHasThumbMap.getOrDefault(questionBankQuestionVO.getId(), false));
//            questionBankQuestionVO.setHasFavour(questionBankQuestionIdHasFavourMap.getOrDefault(questionBankQuestionVO.getId(), false));
        });
        // endregion

        questionBankQuestionVOPage.setRecords(questionBankQuestionVOList);
        return questionBankQuestionVOPage;
    }

    /**
     * 批量添加题目到题库
     *
     * @param questionIdList 题目id列表
     * @param questionBankId 题库id
     * @param loginUser 用户
     * @return
     */
    @Override
    public void batchAddQuestionsToBank(List<Long> questionIdList, Long questionBankId, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表不能为空");
        ThrowUtils.throwIf(questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库 id 非法");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 检查题目 id 是否存在
        // 合法的题目id列表
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = Wrappers.lambdaQuery(Question.class).select(Question::getId).in(Question::getId, questionIdList);
        List<Long> validQuestionIdList = questionService.listObjs(questionLambdaQueryWrapper, o -> (Long)o);
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.NOT_FOUND_ERROR, "合法的题目id列表为空");
        // 检查哪些题目还不存在于题库中，避免重复插入
        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, validQuestionIdList);
        List<QuestionBankQuestion> existQuestionList = this.list(lambdaQueryWrapper);
        Set<Long> exitQuestionIdSet = existQuestionList.stream().map(QuestionBankQuestion::getQuestionId).collect(Collectors.toSet());
        validQuestionIdList = validQuestionIdList.stream()
                .filter(questionId -> !exitQuestionIdSet.contains(questionId))
                .collect(Collectors.toList());
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.NOT_FOUND_ERROR, "所有题目都已存在于题库中");

        // 检查题库 id 是否存在
        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库不存在");

        // 使用Redis + Redisson实现分布式锁
        RLock lock = redissonClient.getLock("batchAddQuestionsToBankLock");
        try {
            boolean isLocked = lock.tryLock(10, 10, TimeUnit.SECONDS);
            if (isLocked) {
                try {
                    // 自定义线程池(IO 密集型)
                    ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(
                            20, // 核心线程数
                            50, // 最大线程数
                            60L, // 线程空闲存活时间
                            TimeUnit.SECONDS, // 存活时间单位
                            new LinkedBlockingDeque<>(10000), // 阻塞队列容量
                            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：由调用线程处理任务
                    );
                    List<CompletableFuture<Void>> futures = new ArrayList<>();

                    // 分批处理，避免长事务，每次处理1000条数据
                    int batchSize = 1000;
                    int totalQuestionListSize = validQuestionIdList.size();
                    for (int i = 0; i < totalQuestionListSize; i += batchSize) {
                        // 生成每批次的数据
                        List<Long> batchQuestionIdList = validQuestionIdList.subList(i, Math.min(i + batchSize, totalQuestionListSize));
                        List<QuestionBankQuestion> batchQuestionBankQuestionList = batchQuestionIdList.stream().map(questionId -> {
                            QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
                            questionBankQuestion.setQuestionId(questionId);
                            questionBankQuestion.setQuestionBankId(questionBankId);
                            questionBankQuestion.setUserId(loginUser.getId());
                            return questionBankQuestion;
                        }).collect(Collectors.toList());
                        // 使用事务处理每批数据
                        // 这里Spring的事务会失效
//            this.batchAddQuestionsToBankInner(batchQuestionBankQuestionList);
                        // 获取代理
                        QuestionBankQuestionService questionBankQuestionService = (QuestionBankQuestionServiceImpl)AopContext.currentProxy();
                        // 异步处理每批数据，将任务添加到异步任务列表
                        CompletableFuture<Void> voidCompletableFuture = CompletableFuture.runAsync(() -> {
                            questionBankQuestionService.batchAddQuestionsToBankInner(batchQuestionBankQuestionList);
                        }, customExecutor);
                        futures.add(voidCompletableFuture);
                    }
                    // 等待所有批次完成操作
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    // 关闭线程池
                    customExecutor.shutdown();
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            } else {
                log.error("无法获取锁，稍后再试...");
            }
        } catch (InterruptedException e) {
            log.error("并发问题, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
        }
    }


    /**
     * 批量添加题目到题库（事务，仅供内部调用）
     *
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {
        // 执行插入
        try {
            // 之前是for循环使用save方法每条记录单独插入数据库
            // 改为MyBatis-plus支持的saveBatch方法，减少与数据库交互次数，提高性能
            boolean result = this.saveBatch(questionBankQuestions);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        } catch (DataIntegrityViolationException e) {
            log.error("数据库唯一键冲突或违反其他完整性约束, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
        } catch (DataAccessException e) {
            log.error("数据库连接问题、事务问题等导致操作失败, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
        } catch (Exception e) {
            // 捕获其他异常，做通用处理
            log.error("添加题目到题库时发生未知错误，错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        }

    }

    /**
     * 批量从题库中删除题目
     *
     * @param questionIdList 题目id列表
     * @param questionBankId 题库id
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemoveQuestionsFromBank(List<Long> questionIdList, Long questionBankId) {
        // 参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表不能为空");
        ThrowUtils.throwIf(questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库 id 非法");
        // 执行移除
        for (long questionId : questionIdList) {
            // 构造查询
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .eq(QuestionBankQuestion::getQuestionId, questionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            boolean result = this.remove(lambdaQueryWrapper);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "从题库移除题目失败");
        }
    }
}
