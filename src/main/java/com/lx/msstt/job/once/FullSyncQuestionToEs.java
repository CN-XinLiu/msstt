package com.lx.msstt.job.once;

import cn.hutool.core.collection.CollUtil;
import com.lx.msstt.esdao.PostEsDao;
import com.lx.msstt.esdao.QuestionEsDao;
import com.lx.msstt.model.dto.post.PostEsDTO;
import com.lx.msstt.model.dto.question.QuestionEsDTO;
import com.lx.msstt.model.entity.Post;
import com.lx.msstt.model.entity.Question;
import com.lx.msstt.service.PostService;
import com.lx.msstt.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全量同步题目到 es
 *
 * @author lx
 */
// todo 取消注释开启任务
@Component
@Slf4j
public class FullSyncQuestionToEs implements CommandLineRunner {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionEsDao questionEsDao;

    @Override
    public void run(String... args) {
        List<Question> questionList = questionService.list();
        if (CollUtil.isEmpty(questionList)) {
            return;
        }
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("FullSyncQuestionToEs start, total {}", total);
        for (int i = 0; i < total; i += pageSize) {
            int end = Math.min(i + pageSize, total);
            log.info("sync from {} to {}", i, end);
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("FullSyncQuestionToEs end, total {}", total);
    }
}
