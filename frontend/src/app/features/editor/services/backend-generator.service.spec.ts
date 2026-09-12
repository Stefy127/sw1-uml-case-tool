import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api.config';
import { BackendGeneratorService } from './backend-generator.service';

describe('BackendGeneratorService', () => {
  let service: BackendGeneratorService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [BackendGeneratorService, provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(BackendGeneratorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts generation options and requests a zip response', () => {
    const request = { basePackage: 'com.example', artifactId: 'ventas', groupId: 'com.example', projectName: 'Ventas' };
    service.generate('diagram-1', request).subscribe((response) => expect(response.body).toBeTruthy());
    const pending = http.expectOne(`${API_BASE_URL}/diagrams/diagram-1/generate/backend`);
    expect(pending.request.method).toBe('POST');
    expect(pending.request.body).toEqual(request);
    pending.flush(new Blob(['zip']), { status: 200, statusText: 'OK', headers: { 'Content-Type': 'application/zip' } });
  });
});
