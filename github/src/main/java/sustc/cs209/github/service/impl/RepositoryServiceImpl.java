package sustc.cs209.github.service.impl;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.SimpleCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sustc.cs209.github.dao.entity.*;
import sustc.cs209.github.dao.mapper.RepositoryMapper;
import sustc.cs209.github.dao.mapper.UserMapper;
import sustc.cs209.github.dto.CommitsStat;
import sustc.cs209.github.dto.IssueDTO;
import sustc.cs209.github.dto.IssueResolutionDTO;
import sustc.cs209.github.dto.ReleaseStat;
import sustc.cs209.github.service.RepositoryService;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RepositoryServiceImpl implements RepositoryService {

    @Autowired
    private RepositoryMapper repositoryMapper;

    @Autowired
    private UserMapper userMapper;

    private static Properties props = new Properties();

    static {
        props.setProperty("annotators", "tokenize, ssplit, pos, parse");
//        props.setProperty("tokenize.language", "en");
        props.setProperty("coref.algorithm", "neural");
    }

    private static StanfordCoreNLP nlp = new StanfordCoreNLP(props);
//    private static StanfordCoreNLP nlp;

    public int getOpenIssueCount(Integer id) {
        List<Issue> issues = repositoryMapper.getIssues(id);
        int count = 0;
        for (Issue issue : issues) {
            if (issue.getClosed().equals(Boolean.FALSE)) {
                count++;
            }
        }
        return count;
    }

    public int getClosedIssueCount(Integer id) {
        List<Issue> issues = repositoryMapper.getIssues(id);
        int count = 0;
        for (Issue issue : issues) {
            if (issue.getClosed().equals(Boolean.TRUE)) {
                count++;
            }
        }
        return count;
    }

    public int getReleaseCount(Integer id) {
        return repositoryMapper.getReleases(id).size();
    }

    public int getDeveloperCount(Integer id) {
        return repositoryMapper.getContributors(id).size();
    }

    public List<Entry<User, Integer>> getTopCommitter(Integer id) {
        List<Commit> commits = repositoryMapper.getCommits(id);
        List<User> users = repositoryMapper.getContributors(id);
        Map<User, Integer> commitCount = new HashMap<>();
        for (User user : users) {
            commitCount.put(user, 0);
        }
        for (Commit commit : commits) {
            User usr = userMapper.getUserByLogin(commit.getAuthor());
            commitCount.put(usr, commitCount.get(usr) + 1);
//            log.info("user " + usr.toString());
        }
        List<Entry<User, Integer>> res = new ArrayList<Entry<User, Integer>>(commitCount.entrySet());
        Collections.sort(res,new Comparator<Entry<User,Integer>>() {
            public int compare(Entry<User, Integer> o1, Entry<User, Integer> o2) {
                return o1.getValue()-o2.getValue();
            }
        });
        return res.size() > 10 ? res.subList(0, 10): res;
    }

    public IssueResolutionDTO getIssueResolution(Integer id) {
        List<Issue> issues = repositoryMapper.getIssues(id);
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (Issue issue : issues) {
            log.error(issue.toString());
            log.error(issue.getClosed().toString());
            if (issue.getClosed().equals(true)) {
                Long dur = issue.getDuration();
                if (dur != -1) {
                    stats.addValue(dur);
                }
            }
        }
//        Date min = new Date((long) stats.getMin());
//        Date max = new Date((long) stats.getMax());
//        Date mean = new Date((long) stats.getMean());
//        Date median = new Date((long) stats.getPercentile(50));
//        Date q1 = new Date((long) stats.getPercentile(25));
//        Date q3 = new Date((long) stats.getPercentile(75));
//        Date var = new Date((long) stats.getVariance());
//        IssueResolutionDTO dto = new IssueResolutionDTO(min, max, mean, median, var, q1, q3);
        IssueResolutionDTO dto = new IssueResolutionDTO(stats.getMin(), stats.getMax(), stats.getMean(), stats.getPercentile(50), stats.getPercentile(25), stats.getPercentile(75), stats.getStandardDeviation());
        return dto;
    }

    public List<ReleaseStat> getReleaseStats(Integer id) {
        List<Release> releases = repositoryMapper.getReleases(id);
        if (releases.size() == 0) {
            return new ArrayList<ReleaseStat>();
        }
        List<Commit> commits = repositoryMapper.getCommits(id);
        List<ReleaseStat> res = new ArrayList<>();
        res.add(new ReleaseStat(releases.get(0).getTag_name(), -1L, releases.get(0).getCreateat(), 0));
        for (int i = 1; i < releases.size(); i++) {
            res.add(new ReleaseStat(releases.get(i).getTag_name(), releases.get(i).getCreateat()));
            res.get(i).setStart(res.get(i - 1).getEnd());
        }
        res.add(new ReleaseStat("Latest", res.get(res.size() - 1).getEnd(), new Date().getTime(), 0));
        commits.sort(new Comparator<Commit>() {
            public int compare(Commit o1, Commit o2) {
                return o1.getCommitAt().compareTo(o2.getCommitAt());
            }
        });
        for (Commit commit : commits) {
            for (int i = 0; i < res.size(); i++) {
                if (commit.getCommitAt() >= res.get(i).getStart() && commit.getCommitAt() <= res.get(i).getEnd()) {
                    res.get(i).setCommits(res.get(i).getCommits() + 1);
                }
            }
        }
        return res;
    }

    public CommitsStat getCommitsStats(Integer id) {
        Integer[][] stats = new Integer[7][24];
        for (int i = 0; i < stats.length; i++) {
            for (int j = 0; j < stats[i].length; j++) {
                stats[i][j] = 0;
            }
        }
        List<Commit> commits = repositoryMapper.getCommits(id);
        for (int i = 0; i < commits.size(); i++) {
            Date date = new Date(commits.get(i).getCommitAt());
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
//            log.error(" week "+ (cal.get(Calendar.DAY_OF_WEEK) - 1));
//            log.error(" hour "+ cal.get(Calendar.HOUR_OF_DAY));
            stats[cal.get(Calendar.DAY_OF_WEEK) - 1][cal.get(Calendar.HOUR_OF_DAY)]++;
        }
        return new CommitsStat(stats);
    }

// sel: 0: title 1: description 2: comments
    public Map<String, Integer> getIssueTitleKeyWord(Integer id, Boolean noun, Integer sel) {
        List<Issue> issues = repositoryMapper.getIssues(id);
        Map<String, Integer> resNoun = new HashMap<>();
        Map<String, Integer> resVerb = new HashMap<>();
        for (Issue issue : issues) {
            String title = null;
            if (sel == 1) {
                title = issue.getTitle();
            } else if (sel == 2) {
                title = issue.getDescription();
            } else if (sel == 3) {
                title = issue.getComments();
            } else {
                System.err.println("Unsupport selection");
                title = "null";
            }
            CoreDocument doc = new CoreDocument(title);
            nlp.annotate(doc);
            List<CoreSentence> tokens = doc.sentences();
            for (CoreSentence token : tokens) {
                List<String> tag = token.posTags();
//                log.error("tag size "+String.valueOf(tag.size()));
//                log.error("token size "+String.valueOf(token.tokens().size()));
                for (CoreLabel label : token.tokens()) {
//                    log.error("label index " + String.valueOf(label.index()));
                    if (Objects.isNull(tag) || Objects.isNull(tag.get(label.index()-1)))
                        continue;
                    if (tag.get(label.index()-1).equals("NN")
//                            || tag.get(label.index()).equals("NNS")
                    ) {
                        String word = label.originalText();
                        if (resNoun.containsKey(word)) {
                            resNoun.put(word, resNoun.get(word) + 1);
                        } else {
                            resNoun.put(word, 1);
                        }
                    }
                    if (tag.get(label.index()-1).equals("VB")
//                            || tag.get(label.index()).equals("VBD") || tag.get(label.index()).equals("VBG") || tag.get(label.index()).equals("VBN") || tag.get(label.index()).equals("VBP") || tag.get(label.index()).equals("VBZ")
                    ) {
                        String word = label.originalText();
                        if (resVerb.containsKey(word)) {
                            resVerb.put(word, resVerb.get(word) + 1);
                        } else {
                            resVerb.put(word, 1);
                        }
                    }
                }
            }
        }
        return noun ? resNoun : resVerb;
    }

    public ReleaseStat nextReleaseCommitCount(Integer id) {
        List<ReleaseStat> stats = getReleaseStats(id);
        if (stats.size() == 0) {
            return new ReleaseStat("No Release", -1L, -1L, 0);
        }
        List<double[]> data = new ArrayList<>();
        Double avgDuration = 0D;
        for (int i = 1; i < stats.size() - 2; i++) {
            data.add(new double[]{stats.get(i).getDuration(), stats.get(i).getCommits()});
            avgDuration += stats.get(i).getDuration();
        }
        avgDuration /= data.size();
        double[][] inputt = data.stream().toArray(double[][]::new);

        ParametricUnivariateFunction func = new PolynomialFunction.Parametric();
        double[] guess = new double[]{1, 1, 1};
        SimpleCurveFitter fitter = SimpleCurveFitter.create(func, guess);
        WeightedObservedPoints obs = new WeightedObservedPoints();
        for (int i = 0; i < inputt.length; i++) {
            obs.add(inputt[i][0], inputt[i][1]);
        }
        double[] coeff = fitter.fit(obs.toList());
        ReleaseStat last_release = stats.get(stats.size()-1);
        ReleaseStat pred = new ReleaseStat();
        pred.setRelease(last_release.getRelease());
        pred.setStart(last_release.getStart());
        pred.setEnd(last_release.getStart() + avgDuration.longValue());
        Integer pred_commits = (int) (coeff[0] + coeff[1] * avgDuration + coeff[2] * avgDuration * avgDuration);
        pred.setCommits(pred_commits);
        return pred;
    }

    public List<IssueDTO> getIssues(Integer id) {
        List<Issue> issues = repositoryMapper.getIssues(id);
        List<IssueDTO> res = new ArrayList<>();
        for (Issue issue : issues) {
            res.add(new IssueDTO(issue.getDisplay(), issue.getCreateat(), issue.getDuration()));
        }
        return res;
    }


}