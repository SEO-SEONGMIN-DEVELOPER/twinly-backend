package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.dto.result.AdminSurveyResult;
import com.nidus.twinly.admin.reader.AdminSurveyReader;
import com.nidus.twinly.common.tendency.TendencyLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminSurveyService {

    private final AdminSurveyReader adminSurveyReader;
    private final TendencyLoader tendencyLoader;

    public AdminSurveyResult survey() {
        return adminSurveyReader.read(tendencyLoader.getAllQuestions());
    }
}
