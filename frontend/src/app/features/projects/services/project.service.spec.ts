import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ProjectService } from './project.service';

describe('ProjectService', () => {
  let service: ProjectService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProjectService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProjectService);
    http = TestBed.inject(HttpTestingController);
  });

  it('lists projects by owner', () => {
    service.getProjectsByOwner('owner-1').subscribe();
    const request = http.expectOne(`${API_BASE_URL}/projects?ownerUserId=owner-1`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates a project', () => {
    const body = { name: 'Demo', description: 'Test', ownerUserId: 'owner-1' };
    service.createProject(body).subscribe();
    const request = http.expectOne(`${API_BASE_URL}/projects`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(body);
  });
});
