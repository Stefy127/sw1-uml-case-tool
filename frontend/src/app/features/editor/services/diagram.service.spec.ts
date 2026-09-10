import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api.config';
import { DiagramService } from './diagram.service';

describe('DiagramService', () => {
  let service: DiagramService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DiagramService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DiagramService);
    http = TestBed.inject(HttpTestingController);
  });

  it('lists diagrams by project', () => {
    service.getDiagramsByProject('project-1').subscribe();
    const request = http.expectOne(`${API_BASE_URL}/diagrams?projectId=project-1`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('gets a diagram detail', () => {
    service.getDiagramById('diagram-1').subscribe();
    const request = http.expectOne(`${API_BASE_URL}/diagrams/diagram-1`);
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('creates a diagram', () => {
    service.createDiagram('project-1', 'Class model').subscribe();
    const request = http.expectOne(`${API_BASE_URL}/diagrams`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ projectId: 'project-1', name: 'Class model' });
    request.flush({});
  });

  it('exports a diagram as XMI blob', () => {
    service.exportXmi('diagram-1').subscribe();
    const request = http.expectOne(`${API_BASE_URL}/diagrams/diagram-1/export/xmi`);
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['<xmi:XMI/>'], { type: 'application/xml' }), { status: 200, statusText: 'OK' });
  });
});
