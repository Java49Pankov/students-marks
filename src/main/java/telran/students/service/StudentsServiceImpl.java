package telran.students.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.bson.Document;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import telran.exceptions.NotFoundException;
import telran.students.dto.*;
import telran.students.model.StudentDoc;
import telran.students.repo.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentsServiceImpl implements StudentsService {
	private static final String MARKS_SCORE_FIELD = "marks.score";
	private static final String MARKS_DATE_FIELD = "marks.date";
	private static final Object SUBJECT_FIELD = "subject";
	private static final Object DATE_FIELD = "date";
	private static final Object SCORE_FIELD = "score";
	private static final String MARKS_FIELD = "marks";
	private static final String AVG_MARK_FIELD = "avgMark";
	private static final int BEST_STUDENTS_SCORE_THRESHOLD = 80;
	private static final String NAME_FIELD = "name";
	private static final String COUNT_FIELD = "count";
	private static final String SUM_SCORES_FIELD = "sum_scores";

	final StudentRepo studentRepo;
	final MongoTemplate mongoTemplate;

	@Override
	@Transactional
	public Student addStudent(Student student) {
		long id = student.id();
		if (studentRepo.existsById(id)) {
			throw new IllegalStateException(String.format("Student %d allredy exists", id));
		}
		studentRepo.save(StudentDoc.of(student));
		log.debug("saved {}", student);
		return student;
	}

	@Override
	@Transactional
	public Student updatePhone(long id, String phone) {
		StudentDoc studentDoc = getStudent(id);
		studentDoc.setPhone(phone);
		String oldPhone = studentDoc.getPhone();
		studentRepo.save(studentDoc);
		log.debug("Student {}, old phone number {}, new phone number {}", id, oldPhone, phone);
		return studentDoc.buildDto();
	}

	private StudentDoc getStudent(long id) {
		return studentRepo.findById(id)
				.orElseThrow(() -> new NotFoundException(String.format("Student %d not found", id)));
	}

	@Override
	@Transactional
	public List<Mark> addMark(long id, Mark mark) {
		StudentDoc studentDoc = getStudent(id);
		studentDoc.addMark(mark);
		studentRepo.save(studentDoc);
		log.debug("Student {} added mark {}", id, mark);
		return studentDoc.getMarks();
	}

	@Override
	@Transactional
	public Student removeStudent(long id) {
		StudentDoc studentDoc = studentRepo.findStudentNoMarks(id);
		if (studentDoc == null) {
			throw new NotFoundException(String.format("Student %d not found", id));
		}
		studentRepo.deleteById(id);
		log.debug("removed student {}, marks {}", studentDoc.getId(), studentDoc.getMarks());
		return studentDoc.buildDto();
	}

	@Override
	@Transactional(readOnly = true)
	public List<Mark> getMarks(long id) {
		StudentDoc studentDoc = studentRepo.findStudentMarks(id);
		if (studentDoc == null) {
			throw new NotFoundException(String.format("Student %d not found", id));
		}
		log.debug("student id {}, name {}, phone {}, marks{} ",
				studentDoc.getId(), studentDoc.getName(), studentDoc.getPhone(), studentDoc.getMarks());
		return studentDoc.getMarks();
	}

	@Override
	public Student getStudentByPhone(String phoneNumber) {
		IdName studentDoc = studentRepo.findByPhone(phoneNumber);
		Student result = null;
		if (studentDoc != null) {
			result = new Student(studentDoc.getId(), studentDoc.getName(), phoneNumber);
		}
		return result;
	}

	@Override
	public List<Student> getStudentsByPhonePrefix(String phonePrefix) {
		List<IdNamePhone> students = studentRepo.findByPhoneRegex(phonePrefix + ".+");
		log.debug("number of the students having phone prefix {} is {}", phonePrefix, students.size());
		return getStudents(students);
	}

	private List<Student> getStudents(List<IdNamePhone> students) {
		return students
				.stream()
				.map(inp -> new Student(inp.getId(), inp.getName(), inp.getPhone())).toList();
	}

	@Override
	public List<Student> getStudentsAllMarsGoodMarks(int thresholdScore) {
		List<IdNamePhone> students = studentRepo.findByGoodMarks(thresholdScore);
		return getStudents(students);
	}

	@Override
	public List<Student> getStudentsFewMarks(int thresholdMarks) {
		List<IdNamePhone> students = studentRepo.findByFewMarks(thresholdMarks);
		return getStudents(students);
	}

	@Override
	public List<Student> getStudentsAllGoodMarksSubject(String subject, int thresholdScore) {
		List<IdNamePhone> students = studentRepo.findByGoodMarksSubject(subject, thresholdScore);
		return getStudents(students);
	}

	@Override
	public List<Student> getStudentsMarksAmountBetween(int min, int max) {
		List<IdNamePhone> students = studentRepo.findByRangeMarks(min, max);
		return getStudents(students);
	}

	@Override
	public List<Mark> getStudentSubjectMarks(long id, String subject) {
		MatchOperation matchMarksSubject = Aggregation.match(Criteria.where("marks.subject").is(subject));
		List<Mark> marks = getStydentMarksByMatchOperation(id, matchMarksSubject);
		log.debug("student {}, marks of subject {} are {}", id, subject, marks);
		return marks;
	}

	private List<Mark> getStydentMarksByMatchOperation(long id, MatchOperation matchOperation) {
		if (!studentRepo.existsById(id)) {
			throw new NotFoundException(String.format("student with id %d not found", id));
		}
		MatchOperation matchStudent = Aggregation.match(Criteria.where("id").is(id));
		UnwindOperation unwindOperation = Aggregation.unwind(MARKS_FIELD);
		ProjectionOperation projectionOperation = Aggregation.project(MARKS_SCORE_FIELD, MARKS_DATE_FIELD,
				"marks.subject");
		Aggregation pipeLine = Aggregation.newAggregation(matchStudent, unwindOperation, matchOperation,
				projectionOperation);
		var aggregationResult = mongoTemplate.aggregate(pipeLine, StudentDoc.class, Document.class);
		List<Document> listDocuments = aggregationResult.getMappedResults();
		log.debug("listDocuments: {}", listDocuments);
		List<Mark> result = listDocuments
				.stream()
				.map(d -> new Mark(d.getString(SUBJECT_FIELD), d.getDate(DATE_FIELD).toInstant()
						.atZone(ZoneId.systemDefault()).toLocalDate(), d.getInteger(SCORE_FIELD)))
				.toList();
		;
		log.debug("result: {}", result);
		return result;
	}

	@Override
	public List<NameAvgScore> getStudentAvgScoreGreater(int avgScoreThreshold) {
		UnwindOperation unwindOperation = Aggregation.unwind(MARKS_FIELD);
		GroupOperation groupOperation = Aggregation.group(NAME_FIELD).avg(MARKS_SCORE_FIELD).as(AVG_MARK_FIELD);
		MatchOperation matchOperation = Aggregation.match(Criteria.where(AVG_MARK_FIELD).gt(avgScoreThreshold));
		SortOperation sortOperation = Aggregation.sort(Direction.DESC, AVG_MARK_FIELD);
		Aggregation pipeLine = Aggregation.newAggregation(unwindOperation, groupOperation, matchOperation,
				sortOperation);

		List<NameAvgScore> res = mongoTemplate
				.aggregate(pipeLine, StudentDoc.class, Document.class)
				.getMappedResults()
				.stream()
				.map(d -> new NameAvgScore(d.getString("_id"), d.getDouble(AVG_MARK_FIELD)
						.intValue()))
				.toList();
		log.debug("students with average score greater than {} are {}", avgScoreThreshold, res);
		return res;
	}

	@Override
	public List<Mark> getStudentMarksAtDates(long id, LocalDate from, LocalDate to) {
		MatchOperation matchMarksDates = Aggregation.match(Criteria.where(MARKS_DATE_FIELD).gte(from).lte(to));
		List<Mark> marks = getStydentMarksByMatchOperation(id, matchMarksDates);
		log.debug("student {}, marks from {} to {} are {}", id, from, to, marks);
		return marks;
	}

	@Override
	public List<String> getBestStudents(int nStudents) {
		UnwindOperation unwindOperation = Aggregation.unwind(MARKS_FIELD);
		MatchOperation matchOperation = Aggregation.match(Criteria.where(MARKS_SCORE_FIELD)
				.gt(BEST_STUDENTS_SCORE_THRESHOLD));
		GroupOperation groupOperation = Aggregation.group(NAME_FIELD).count().as(COUNT_FIELD);
		SortOperation sortOperation = Aggregation.sort(Direction.DESC, COUNT_FIELD);
		ProjectionOperation projectionOperation = Aggregation.project(NAME_FIELD);
		LimitOperation limitOperation = Aggregation.limit(nStudents);
		Aggregation pipeLine = Aggregation.newAggregation(unwindOperation, matchOperation, groupOperation,
				sortOperation, projectionOperation, limitOperation);
		List<String> res = mongoTemplate.aggregate(pipeLine, StudentDoc.class, Document.class)
				.getMappedResults()
				.stream()
				.map(d -> d.getString("_id")).toList();
		log.debug("{} best students are {}", nStudents, res);
		return res;
	}

	@Override
	public List<String> getWorstStudents(int nStudents) {
		AggregationExpression expression = AccumulatorOperators.Sum.sumOf(MARKS_SCORE_FIELD);
		ProjectionOperation projectionOperation = Aggregation.project(NAME_FIELD).and(expression)
				.as(SUM_SCORES_FIELD);
		SortOperation sortOperation = Aggregation.sort(Direction.ASC, SUM_SCORES_FIELD);
		LimitOperation limitOperation = Aggregation.limit(nStudents);
		ProjectionOperation projectionOperationOnlyName = Aggregation.project(NAME_FIELD);
		Aggregation pipeLine = Aggregation.newAggregation(projectionOperation, sortOperation, limitOperation,
				projectionOperationOnlyName);
		List<String> res = mongoTemplate
				.aggregate(pipeLine, StudentDoc.class, Document.class)
				.getMappedResults()
				.stream()
				.map(d -> d.getString(NAME_FIELD))
				.toList();
		log.debug("{} worst students are {}", nStudents, res);
		return res;
	}

}
