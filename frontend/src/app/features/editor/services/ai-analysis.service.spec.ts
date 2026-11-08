import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api.config';
import { AiAnalysisService } from './ai-analysis.service';

describe('AiAnalysisService', () => {
  let service: AiAnalysisService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [AiAnalysisService, provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AiAnalysisService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the analysis request using the project id, not the diagram id', () => {
    service.analyze('project-42').subscribe();
    const request = http.expectOne(`${API_BASE_URL}/projects/project-42/ai/analyze`);
    expect(request.request.method).toBe('POST');
    request.flush({ projectId: 'project-42', summary: 'ok', findings: [] });
  });
});
